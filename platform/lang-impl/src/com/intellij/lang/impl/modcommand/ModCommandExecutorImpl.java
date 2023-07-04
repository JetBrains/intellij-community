// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.template.*;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.lang.LangBundle;
import com.intellij.modcommand.*;
import com.intellij.modcommand.ModCommandAction.ActionContext;
import com.intellij.modcommand.ModUpdateFileText.Fragment;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class ModCommandExecutorImpl implements ModCommandExecutor {
  @RequiresEdt
  @Override
  public void executeInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor) {
    if (!ensureWritable(context.project(), command)) return;
    doExecute(context, command, editor, true);
  }

  private static boolean ensureWritable(@NotNull Project project, @NotNull ModCommand command) {
    Set<VirtualFile> files = command.modifiedFiles();
    return files.isEmpty() || ReadonlyStatusHandler.ensureFilesWritable(project, files.toArray(VirtualFile.EMPTY_ARRAY));
  }

  @Override
  public void executeInBatch(@NotNull ActionContext context, @NotNull ModCommand command) {
    if (!ensureWritable(context.project(), command)) return;
    doExecute(context, command, null, false);
  }

  private boolean doExecute(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor, boolean onTheFly) {
    Project project = context.project();
    if (command instanceof ModUpdateFileText upd) {
      return executeUpdate(project, upd);
    }
    if (command instanceof ModCompositeCommand cmp) {
      return executeComposite(context, cmp, editor, onTheFly);
    }
    if (command instanceof ModNavigate nav) {
      if (!onTheFly) return true;
      return executeNavigate(project, nav);
    }
    if (command instanceof ModHighlight highlight) {
      if (!onTheFly) return true;
      return executeHighlight(project, highlight);
    }
    if (command instanceof ModNothing) {
      return true;
    }
    if (command instanceof ModChooseAction chooser) {
      return executeChoose(context, chooser, onTheFly, editor);
    }
    if (command instanceof ModDisplayMessage message) {
      if (!onTheFly) return true; // TODO: gather all errors and display them together?
      return executeMessage(project, message);
    }
    if (command instanceof ModRenameSymbol rename) {
      if (!onTheFly) return true;
      return executeRename(project, rename);
    }
    if (command instanceof ModCreateFile create) {
      return executeCreate(project, create);
    }
    if (command instanceof ModDeleteFile deleteFile) {
      return executeDelete(project, deleteFile);
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  private boolean executeDelete(Project project, ModDeleteFile file) {
    try {
      WriteAction.run(() -> file.file().delete(this));
      return true;
    }
    catch (IOException e) {
      executeMessage(project, new ModDisplayMessage(e.getMessage(), ModDisplayMessage.MessageKind.ERROR));
      return false;
    }
  }

  private boolean executeCreate(@NotNull Project project, @NotNull ModCreateFile create) {
    FutureVirtualFile file = create.file();
    VirtualFile parent = actualize(file.getParent());
    try {
      return WriteAction.compute(() -> {
        VirtualFile newFile = parent.createChildData(this, file.getName());
        PsiFile psiFile = PsiManager.getInstance(project).findFile(newFile);
        if (psiFile == null) return false;
        Document document = psiFile.getViewProvider().getDocument();
        document.setText(create.text());
        PsiDocumentManager.getInstance(project).commitDocument(document);
        return true;
      });
    }
    catch (IOException e) {
      executeMessage(project, new ModDisplayMessage(e.getMessage(), ModDisplayMessage.MessageKind.ERROR));
      return false;
    }
  }

  private static boolean executeRename(Project project, ModRenameSymbol rename) {
    VirtualFile file = actualize(rename.file());
    if (file == null) return false;
    PsiFile psiFile = PsiManagerEx.getInstanceEx(project).findFile(file);
    if (psiFile == null) return false;
    PsiNameIdentifierOwner element =
      PsiTreeUtil.getNonStrictParentOfType(psiFile.findElementAt(rename.symbolRange().getStartOffset()), PsiNameIdentifierOwner.class);
    if (element == null) return false;
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    final Editor editor = fileEditorManager.getSelectedTextEditor();
    if (editor == null || !editor.getVirtualFile().equals(file)) return false;
    PsiElement nameIdentifier = element.getNameIdentifier();
    if (nameIdentifier == null) return false;
    SmartPsiElementPointer<PsiNameIdentifierOwner> pointer = SmartPointerManager.createPointer(element);
    record RenameData(Collection<PsiReference> references, PsiElement scope, PsiNameIdentifierOwner nameOwner) {
      static RenameData create(SmartPsiElementPointer<? extends PsiNameIdentifierOwner> pointer) {
        PsiNameIdentifierOwner owner = pointer.getElement();
        if (owner == null) return null;
        PsiFile psiFile = owner.getContainingFile();
        Collection<PsiReference> references = ReferencesSearch.search(owner, new LocalSearchScope(psiFile)).findAll();
        PsiElement scope = PsiTreeUtil.findCommonParent(ContainerUtil.map(references, ref -> ref.getElement()));
        if (scope != null) {
          scope = PsiTreeUtil.findCommonParent(scope, owner);
        }
        if (scope == null) {
          scope = psiFile;
        }
        return new RenameData(references, scope, owner);
      }
    }
    ReadAction.nonBlocking(() -> RenameData.create(pointer)).expireWhen(() -> editor.isDisposed())
      .finishOnUiThread(ModalityState.defaultModalityState(), renameData -> {
        final TextRange textRange = renameData.scope().getTextRange();
        final int startOffset = textRange.getStartOffset();
        final TemplateBuilderImpl builder = new TemplateBuilderImpl(renameData.scope());
        final Expression nameExpression = new NameExpression(element.getName(), rename.nameSuggestions());
        final PsiElement identifier = renameData.nameOwner().getNameIdentifier();
        builder.replaceElement(identifier, "PATTERN", nameExpression, true);
        for (PsiReference reference : renameData.references()) {
          builder.replaceElement(reference, "PATTERN", "PATTERN", false);
        }
        CommandProcessor.getInstance().executeCommand(project, () -> {
          final Template template = WriteAction.compute(builder::buildInlineTemplate);
          editor.getCaretModel().moveToOffset(startOffset);
          final TemplateManager templateManager = TemplateManager.getInstance(project);
          templateManager.startTemplate(editor, template);
        }, LangBundle.message("action.rename.text"), null);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
    return true;
  }

  static class NameExpression extends Expression {
    private final String myOrig;
    private final LookupElement[] cachedLookupElements;

    NameExpression(String orig, List<String> suggestions) {
      myOrig = orig;
      cachedLookupElements = suggestions.stream()
        .map(LookupElementBuilder::create)
        .toArray(LookupElement[]::new);
    }

    @Override
    public boolean requiresCommittedPSI() {
      return false;
    }

    @Override
    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myOrig);
    }

    @Override
    public @NotNull LookupFocusDegree getLookupFocusDegree() {
      return LookupFocusDegree.UNFOCUSED;
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return cachedLookupElements;
    }
  }

  private static boolean executeMessage(@NotNull Project project, @NotNull ModDisplayMessage message) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return false;
    switch (message.kind()) {
      case INFORMATION -> HintManager.getInstance().showInformationHint(editor, message.messageText());
      case ERROR -> HintManager.getInstance().showErrorHint(editor, message.messageText());
    }
    return true;
  }

  private boolean executeChoose(@NotNull ActionContext context, ModChooseAction chooser, boolean onTheFly, @Nullable Editor editor) {
    record ActionAndPresentation(@NotNull ModCommandAction action, @NotNull ModCommandAction.Presentation presentation) {}
    List<ActionAndPresentation> actions = StreamEx.of(chooser.actions()).mapToEntry(action -> action.getPresentation(context))
      .nonNullValues().mapKeyValue(ActionAndPresentation::new).toList();
    if (actions.isEmpty()) return true;
    
    String name = chooser.title();
    if (actions.size() == 1 || !onTheFly) {
      ModCommandAction action = actions.get(0).action();
      executeNextStep(context, name, editor, onTheFly, () -> {
        if (action.getPresentation(context) == null) return null;
        return action.perform(context);
      });
      return true;
    }
    VirtualFile file = context.file().getVirtualFile();
    if (file == null) return false;
    Editor finalEditor = editor == null ? getEditor(context.project(), file) : editor;
    if (editor == null) return false;
    ShowIntentionsPass.IntentionsInfo info = new ShowIntentionsPass.IntentionsInfo();
    List<HighlightInfo.IntentionActionDescriptor> descriptors = ContainerUtil.map(
      actions, (actionAndPresentation) -> {
        ModCommandAction.Presentation presentation = actionAndPresentation.presentation();
        IntentionAction intention = actionAndPresentation.action().asIntention();
        intention.isAvailable(context.project(), finalEditor, context.file()); // cache text
        return new HighlightInfo.IntentionActionDescriptor(intention, List.of(), presentation.name(), presentation.icon(), null, null, null);
      });
    info.intentionsToShow.addAll(descriptors);
    info.setTitle(chooser.title());
    CachedIntentions intentions = CachedIntentions.create(context.project(), context.file(), editor, info);
    IntentionHintComponent.showIntentionHint(context.project(), context.file(), editor, true, intentions);
    return true;
  }

  private void executeNextStep(@NotNull ActionContext context, @NotNull @NlsContexts.Command String name, @Nullable Editor editor, 
                               boolean onTheFly, Callable<? extends ModCommand> supplier) {
    ModCommand next = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        return ReadAction.nonBlocking(supplier).expireWhen(context.project()::isDisposed).executeSynchronously();
      }, name, true, context.project());
    if (next == null) return;
    CommandProcessor.getInstance().executeCommand(context.project(), () -> executeInteractively(context, next, editor), name, onTheFly);
  }

  private static VirtualFile actualize(@NotNull VirtualFile file) {
    return file instanceof FutureVirtualFile future ? actualize(future.getParent()).findChild(future.getName()) : file;
  }

  private static boolean executeNavigate(@NotNull Project project, ModNavigate nav) {
    VirtualFile file = actualize(nav.file());
    if (file == null) return false;
    int selectionStart = nav.selectionStart();
    int selectionEnd = nav.selectionEnd();
    int caret = nav.caret();

    Editor editor = getEditor(project, file);
    if (editor == null) return false;
    if (selectionStart != -1 && selectionEnd != -1) {
      editor.getSelectionModel().setSelection(selectionStart, selectionEnd);
    }
    if (caret != -1) {
      editor.getCaretModel().moveToOffset(caret);
    }
    return true;
  }

  private static boolean executeHighlight(Project project, ModHighlight highlight) {
    VirtualFile file = actualize(highlight.virtualFile());
    if (file == null) return false;
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
    if (!(fileEditor instanceof TextEditor textEditor)) return false;
    Editor editor = textEditor.getEditor();
    HighlightManager manager = HighlightManager.getInstance(project);
    for (ModHighlight.HighlightInfo info : highlight.highlights()) {
      manager.addRangeHighlight(editor, info.range().getStartOffset(), info.range().getEndOffset(), info.attributesKey(),
                                 info.hideByTextChange(), null);
    }
    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
    return true;
  }

  private static Editor getEditor(@NotNull Project project, VirtualFile file) {
    return FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file), true);
  }

  private boolean executeComposite(@NotNull ActionContext context, ModCompositeCommand cmp, @Nullable Editor editor, boolean onTheFly) {
    for (ModCommand command : cmp.commands()) {
      boolean status = doExecute(context, command, editor, onTheFly);
      if (!status) {
        return false;
      }
    }
    return true;
  }

  private static boolean executeUpdate(@NotNull Project project, @NotNull ModUpdateFileText upd) {
    VirtualFile file = upd.file();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;
    String oldText = upd.oldText();
    String newText = upd.newText();
    if (!document.getText().equals(oldText)) return false;
    List<@NotNull Fragment> ranges = calculateRanges(upd);
    return WriteAction.compute(() -> {
      for (Fragment range : ranges) {
        document.replaceString(range.offset(), range.offset() + range.oldLength(),
                               newText.substring(range.offset(), range.offset() + range.newLength()));
      }
      PsiDocumentManager.getInstance(project).commitDocument(document);
      return true;
    });
  }
  
  private static @NotNull List<@NotNull Fragment> calculateRanges(@NotNull ModUpdateFileText upd) {
    List<@NotNull Fragment> ranges = upd.updatedRanges();
    if (!ranges.isEmpty()) return ranges;
    String oldText = upd.oldText();
    String newText = upd.newText();
    List<DiffFragment> fragments = ComparisonManager.getInstance().compareChars(oldText, newText, ComparisonPolicy.DEFAULT,
                                                                                DumbProgressIndicator.INSTANCE);
    return ContainerUtil.map(fragments, fr -> new Fragment(fr.getStartOffset2(), fr.getEndOffset1() - fr.getStartOffset1(),
                                                           fr.getEndOffset2() - fr.getStartOffset2()));
  }
}
