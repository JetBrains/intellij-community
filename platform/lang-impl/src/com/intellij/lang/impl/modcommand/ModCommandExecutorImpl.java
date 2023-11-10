// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInsight.intention.impl.IntentionContainer;
import com.intellij.codeInsight.intention.impl.IntentionGroup;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.LangBundle;
import com.intellij.modcommand.*;
import com.intellij.modcommand.ModChooseMember.SelectionMode;
import com.intellij.modcommand.ModUpdateFileText.Fragment;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pass;
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
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.suggested.*;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNullElse;

public class ModCommandExecutorImpl implements ModCommandExecutor {
  @RequiresEdt
  @Override
  public void executeInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor) {
    if (!ensureWritable(context.project(), command)) return;
    doExecuteInteractively(context, command, editor);
  }

  private static boolean ensureWritable(@NotNull Project project, @NotNull ModCommand command) {
    Collection<VirtualFile> files = ContainerUtil.filter(command.modifiedFiles(), f -> !(f instanceof LightVirtualFile));
    return files.isEmpty() || ReadonlyStatusHandler.ensureFilesWritable(project, files.toArray(VirtualFile.EMPTY_ARRAY));
  }

  @Override
  public @NotNull BatchExecutionResult executeInBatch(@NotNull ActionContext context, @NotNull ModCommand command) {
    if (!ensureWritable(context.project(), command)) {
      return new Error(LangBundle.message("executor.error.files.are.marked.as.readonly"));
    }
    return doExecuteInBatch(context, command);
  }

  private BatchExecutionResult doExecuteInBatch(@NotNull ActionContext context, @NotNull ModCommand command) {
    Project project = context.project();
    if (command instanceof ModNothing) {
      return Result.NOTHING;
    }
    if (command instanceof ModUpdateFileText upd) {
      return executeUpdate(project, upd) ? Result.SUCCESS : Result.ABORT;
    }
    if (command instanceof ModCreateFile create) {
      return executeCreate(project, create) ? Result.SUCCESS : Result.ABORT;
    }
    if (command instanceof ModDeleteFile deleteFile) {
      return executeDelete(project, deleteFile) ? Result.SUCCESS : Result.ABORT;
    }
    if (command instanceof ModCompositeCommand cmp) {
      BatchExecutionResult result = Result.NOTHING;
      for (ModCommand subCommand : cmp.commands()) {
        result = result.compose(doExecuteInBatch(context, subCommand));
        if (result == Result.ABORT || result instanceof Error) break;
      }
      return result;
    }
    if (command instanceof ModChooseAction chooser) {
      return executeChooseInBatch(context, chooser);
    }
    if (command instanceof ModChooseMember member) {
      ModCommand nextCommand = ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> ReadAction.nonBlocking(() -> member.nextCommand().apply(member.defaultSelection())).executeSynchronously(),
        member.title(), true, context.project());
      executeInBatch(context, nextCommand);
    }
    if (command instanceof ModNavigate || command instanceof ModHighlight ||
        command instanceof ModCopyToClipboard || command instanceof ModRenameSymbol ||
        command instanceof ModStartTemplate || command instanceof ModUpdateSystemOptions) {
      return Result.INTERACTIVE;
    }
    if (command instanceof ModShowConflicts showConflicts) {
      if (showConflicts.conflicts().isEmpty()) {
        return executeInBatch(context, showConflicts.nextStep());
      }
      return Result.CONFLICTS;
    }
    if (command instanceof ModDisplayMessage message) {
      if (message.kind() == ModDisplayMessage.MessageKind.ERROR) {
        return new Error(message.messageText());
      }
      return Result.INTERACTIVE;
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  private BatchExecutionResult executeChooseInBatch(@NotNull ActionContext context, ModChooseAction chooser) {
    ModCommandAction action = StreamEx.of(chooser.actions()).filter(act -> act.getPresentation(context) != null)
      .findFirst().orElse(null);
    if (action == null) return Result.NOTHING;

    String name = chooser.title();
    ModCommand next = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.nonBlocking(() -> {
      if (action.getPresentation(context) == null) return null;
      return action.perform(context);
    }).executeSynchronously(), name, true, context.project());
    if (next == null) return Result.ABORT;
    return executeInBatch(context, next);
  }

  private boolean doExecuteInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor) {
    Project project = context.project();
    if (command instanceof ModUpdateFileText upd) {
      return executeUpdate(project, upd);
    }
    if (command instanceof ModUpdateReferences decl) {
      return !executeTrackDeclaration(context, decl, editor);
    }
    if (command instanceof ModCompositeCommand cmp) {
      return executeComposite(context, cmp, editor);
    }
    if (command instanceof ModNavigate nav) {
      return executeNavigate(project, nav);
    }
    if (command instanceof ModHighlight highlight) {
      return executeHighlight(project, highlight);
    }
    if (command instanceof ModCopyToClipboard copyToClipboard) {
      return executeCopyToClipboard(copyToClipboard);
    }
    if (command instanceof ModNothing) {
      return true;
    }
    if (command instanceof ModChooseAction chooser) {
      return executeChoose(context, chooser, editor);
    }
    if (command instanceof ModChooseMember chooser) {
      return executeChooseMember(context, chooser, editor);
    }
    if (command instanceof ModDisplayMessage message) {
      return executeMessage(project, message);
    }
    if (command instanceof ModRenameSymbol rename) {
      return executeRename(project, rename, editor);
    }
    if (command instanceof ModCreateFile create) {
      return executeCreate(project, create);
    }
    if (command instanceof ModDeleteFile deleteFile) {
      return executeDelete(project, deleteFile);
    }
    if (command instanceof ModShowConflicts showConflicts) {
      return executeShowConflicts(context, showConflicts, editor);
    }
    if (command instanceof ModStartTemplate startTemplate) {
      return executeStartTemplate(context, startTemplate, editor);
    }
    if (command instanceof ModUpdateSystemOptions updateOptions) {
      return executeUpdateInspectionOptions(context, updateOptions);
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  @Nullable
  private static PsiElement findElementAtRange(PsiFile psiFile, TextRange declarationRange) {
    PsiElement element = psiFile.findElementAt(declarationRange.getStartOffset());
    while (element != null && !element.getTextRange().contains(declarationRange)) {
      element = element.getParent();
    }
    if (element == null || !element.getTextRange().equals(declarationRange)) return null;
    return element;
  }

  private static boolean executeTrackDeclaration(@NotNull ActionContext context, @NotNull ModUpdateReferences decl, @Nullable Editor editor) {
    // TODO: properly support multiple tracked declarations
    VirtualFile file = decl.file();
    Project project = context.project();
    Callable<SuggestedRefactoringState> computeNewState = () -> {
      PsiFile psiFile = PsiManagerEx.getInstanceEx(project).findFile(file);
      if (psiFile == null) return null;
      SuggestedRefactoringSupport support = SuggestedRefactoringSupport.Companion.forLanguage(psiFile.getLanguage());
      if (support == null) return null;
      PsiElement newElement = findElementAtRange(psiFile, decl.newRange());
      if (newElement == null || !support.isAnchor(newElement)) return null;
      SuggestedRefactoringStateChanges stateChanges = support.getStateChanges();
      PsiFile fileCopy = (PsiFile)psiFile.copy();
      Document documentCopy = fileCopy.getViewProvider().getDocument();
      documentCopy.replaceString(0, documentCopy.getTextLength(), decl.oldText());
      PsiDocumentManager.getInstance(project).commitDocument(documentCopy);
      PsiElement element = findElementAtRange(fileCopy, decl.oldRange());
      if (element == null) return null;
      if (!support.isAnchor(element) || stateChanges.findDeclaration(element) != element) return null;
      SuggestedRefactoringState state = stateChanges.createInitialState(element);
      if (state == null) return null;
      SuggestedRefactoringAvailability availability = support.getAvailability();
      SuggestedRefactoringState newState = stateChanges.updateState(state, newElement);
      if (newState.getErrorLevel() != SuggestedRefactoringState.ErrorLevel.NO_ERRORS) return null;
      if (availability.detectAvailableRefactoring(newState) != null && availability.isAvailable(newState)) return newState;
      return null;
    };
    SuggestedRefactoringState finalState = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.nonBlocking(computeNewState).executeSynchronously(), 
      LangBundle.message("dialog.title.searching.for.usages"), true, project);
    if (finalState == null) return false;
    Editor finalEditor = getEditor(project, editor, decl.file());
    if (finalEditor == null) return false;
    PerformSuggestedRefactoringKt.performSuggestedRefactoring(
       finalState, finalEditor, project, ActionPlaces.INTENTION_MENU, true, null, null);
    return true;
  }

  private static boolean executeUpdateInspectionOptions(@NotNull ActionContext context, @NotNull ModUpdateSystemOptions options) {
    VirtualFile vFile = context.file().getVirtualFile();
    Project project = context.project();
    setOption(project, vFile, options, true);
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
      @Override
      public void undo() {
        setOption(project, vFile, options, false);
      }

      @Override
      public void redo() {
        setOption(project, vFile, options, true);
      }
    });
    return true;
  }

  private static void setOption(@NotNull Project project, VirtualFile vFile, @NotNull ModUpdateSystemOptions options, boolean newValue) {
    PsiFile file = PsiManager.getInstance(project).findFile(vFile);
    if (file == null) return;
    for (ModUpdateSystemOptions.ModifiedOption option : options.options()) {
      OptionController controller = OptionControllerProvider.rootController(file);
      Object value = newValue ? option.newValue() : option.oldValue();
      if (value instanceof List<?> list) {
        @SuppressWarnings("unchecked")
        List<Object> oldList = (List<Object>)Objects.requireNonNull(controller.getOption(option.bindId()));
        oldList.clear();
        oldList.addAll(list);
        value = oldList;
      }
      controller.setOption(option.bindId(), value);
    }
  }

  private boolean executeChooseMember(@NotNull ActionContext context, @NotNull ModChooseMember modChooser, @Nullable Editor editor) {
    List<? extends @NotNull MemberChooserElement> result;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      result = modChooser.defaultSelection();
    }
    else {
      ClassMember[] members = ContainerUtil.map2Array(modChooser.elements(), ClassMember.EMPTY_ARRAY, ClassMember::from);
      SelectionMode mode = modChooser.mode();
      boolean allowEmptySelection = mode == SelectionMode.SINGLE_OR_EMPTY ||
                                    mode == SelectionMode.MULTIPLE_OR_EMPTY;
      boolean allowMultiSelection = mode == SelectionMode.MULTIPLE ||
                                    mode == SelectionMode.MULTIPLE_OR_EMPTY;
      MemberChooser<ClassMember> chooser = new MemberChooser<>(members, allowEmptySelection, allowMultiSelection, context.project());
      ClassMember[] selected = IntStreamEx.ofIndices(modChooser.elements(), modChooser.defaultSelection()::contains)
        .elements(members).toArray(ClassMember.EMPTY_ARRAY);
      chooser.selectElements(selected);
      chooser.setTitle(modChooser.title());
      chooser.setCopyJavadocVisible(false);
      if (!chooser.showAndGet()) return false;
      List<ClassMember> elements = chooser.getSelectedElements();
      result = elements == null ? List.of() :
               IntStreamEx.ofIndices(members, elements::contains).elements(modChooser.elements()).toList();
    }
    ModCommand nextCommand = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.nonBlocking(() -> modChooser.nextCommand().apply(result)).executeSynchronously(),
      modChooser.title(), true, context.project());
    executeInteractively(context, nextCommand, editor);
    return true;
  }

  private boolean executeStartTemplate(@NotNull ActionContext context, @NotNull ModStartTemplate template, @Nullable Editor editor) {
    VirtualFile file = actualize(template.file());
    if (file == null) return false;
    Editor finalEditor = getEditor(context.project(), editor, file);
    if (finalEditor == null) return false;
    PsiFile psiFile = PsiManagerEx.getInstanceEx(context.project()).findFile(file);
    if (psiFile == null) return false;
    String name = requireNonNullElse(CommandProcessor.getInstance().getCurrentCommandName(),
                                     LangBundle.message("command.title.finishing.template"));
    WriteAction.run(() -> {
      TemplateBuilderImpl builder = new TemplateBuilderImpl(psiFile);
      for (ModStartTemplate.TemplateField field : template.fields()) {
        if (field instanceof ModStartTemplate.ExpressionField expr) {
          builder.replaceElement(psiFile, expr.range(), expr.expression());
        }
        else if (field instanceof ModStartTemplate.DependantVariableField variableField) {
          builder.replaceElement(psiFile, variableField.range(), variableField.varName(),
                                 variableField.dependantVariableName(), variableField.alwaysStopAt());
        }
      }

      final Template tmpl = builder.buildInlineTemplate();
      finalEditor.getCaretModel().moveToOffset(0);
      TemplateManager.getInstance(context.project()).startTemplate(finalEditor, tmpl, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(@NotNull Template tmpl, boolean brokenOff) {
          ModCommand next = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            return ReadAction.nonBlocking(() -> template.templateFinishFunction().apply(psiFile)).executeSynchronously();
          }, name, true, context.project());
          CommandProcessor.getInstance().executeCommand(context.project(), () -> executeInteractively(context, next, editor), name, null);
        }
      });
    });
    return true;
  }

  private boolean executeShowConflicts(@NotNull ActionContext context, @NotNull ModShowConflicts conflicts, @Nullable Editor editor) {
    MultiMap<PsiElement, String> conflictData = new MultiMap<>();
    conflicts.conflicts().forEach((e, c) -> conflictData.put(e, c.messages()));
    if (!conflictData.isEmpty()) {
      var conflictsDialog =
        new ConflictsDialog(context.project(), conflictData, () -> doExecuteInteractively(context, conflicts.nextStep(), editor));
      if (!conflictsDialog.showAndGet()) {
        return false;
      }
    }
    return doExecuteInteractively(context, conflicts.nextStep(), editor);
  }

  private static boolean executeCopyToClipboard(@NotNull ModCopyToClipboard clipboard) {
    CopyPasteManager.getInstance().setContents(new StringSelection(clipboard.content()));
    return true;
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

  private static boolean executeRename(@NotNull Project project, @NotNull ModRenameSymbol rename, @Nullable Editor editor) {
    VirtualFile file = actualize(rename.file());
    if (file == null) return false;
    PsiFile psiFile = PsiManagerEx.getInstanceEx(project).findFile(file);
    if (psiFile == null) return false;
    PsiNameIdentifierOwner element =
      PsiTreeUtil.getNonStrictParentOfType(psiFile.findElementAt(rename.symbolRange().range().getStartOffset()), PsiNameIdentifierOwner.class);
    if (element == null) return false;
    Editor finalEditor = getEditor(project, editor, file);
    if (finalEditor == null) return false;
    PsiElement nameIdentifier = element.getNameIdentifier();
    if (nameIdentifier == null) return false;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      int offset = nameIdentifier.getTextRange().getEndOffset();
      return executeNavigate(project, new ModNavigate(file, offset, offset, offset));
    }
    finalEditor.getCaretModel().moveToOffset(nameIdentifier.getTextOffset());
    final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(element);
    if (!processor.isInplaceRenameSupported()) {
      fallBackRename(project, rename, element, finalEditor);
      return true;
    }
    processor.substituteElementToRename(element, finalEditor, new Pass<>() {
      @Override
      public void pass(PsiElement substitutedElement) {
        final MemberInplaceRenamer renamer = new MemberInplaceRenamer(element, substitutedElement, finalEditor);
        final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<>(rename.nameSuggestions());
        renamer.performInplaceRefactoring(nameSuggestions);
      }
    });
    return true;
  }

  private static void fallBackRename(@NotNull Project project,
                                @NotNull ModRenameSymbol rename,
                                PsiNameIdentifierOwner element,
                                Editor finalEditor) {
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
    ReadAction.nonBlocking(() -> RenameData.create(pointer)).expireWhen(() -> finalEditor.isDisposed())
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
          finalEditor.getCaretModel().moveToOffset(startOffset);
          final TemplateManager templateManager = TemplateManager.getInstance(project);
          templateManager.startTemplate(finalEditor, template);
        }, LangBundle.message("action.rename.text"), null);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Nullable
  private static Editor getEditor(@NotNull Project project, @Nullable Editor editor, VirtualFile file) {
    Editor finalEditor = editor == null || !file.equals(editor.getVirtualFile()) ? getEditor(project, file) : editor;
    if (finalEditor == null) return null;
    return finalEditor;
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
    public com.intellij.codeInsight.template.Result calculateResult(ExpressionContext context) {
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
  
  private static final class ErrorInTestException extends RuntimeException {
    private ErrorInTestException(String message) {
      super(message);
    }
  }

  private static boolean executeMessage(@NotNull Project project, @NotNull ModDisplayMessage message) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor == null) return false;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (message.kind() == ModDisplayMessage.MessageKind.ERROR) {
        throw new ErrorInTestException(message.messageText());
      }
    }
    switch (message.kind()) {
      case INFORMATION -> HintManager.getInstance().showInformationHint(editor, message.messageText());
      case ERROR -> HintManager.getInstance().showErrorHint(editor, message.messageText());
    }
    return true;
  }

  private boolean executeChoose(@NotNull ActionContext context, ModChooseAction chooser, @Nullable Editor editor) {
    record ActionAndPresentation(@NotNull ModCommandAction action, @NotNull Presentation presentation) {}
    List<ActionAndPresentation> actions = StreamEx.of(chooser.actions()).mapToEntry(action -> action.getPresentation(context))
      .nonNullValues().mapKeyValue(ActionAndPresentation::new).toList();
    if (actions.isEmpty()) return true;
    
    String name = chooser.title();
    if (actions.size() == 1) {
      ModCommandAction action = actions.get(0).action();
      executeNextStep(context, name, editor, () -> {
        if (action.getPresentation(context) == null) return null;
        return action.perform(context);
      });
      return true;
    }
    VirtualFile file = context.file().getVirtualFile();
    if (file == null) return false;
    Editor finalEditor = editor == null ? getEditor(context.project(), file) : editor;
    if (editor == null) return false;
    List<IntentionActionWithTextCaching> actionsWithTextCaching = ContainerUtil.map(
      actions, (actionAndPresentation) -> {
        IntentionAction intention = actionAndPresentation.action().asIntention();
        intention.isAvailable(context.project(), finalEditor, context.file()); // cache text and icon
        return new IntentionActionWithTextCaching(intention);
      });
    IntentionContainer intentions = new IntentionContainer() {
      @Override
      public @NotNull String getTitle() {
        return chooser.title();
      }

      @Override
      public @NotNull List<IntentionActionWithTextCaching> getAllActions() {
        return actionsWithTextCaching;
      }

      @Override
      public @NotNull IntentionGroup getGroup(@NotNull IntentionActionWithTextCaching action) {
        return IntentionGroup.OTHER;
      }

      @Override
      public @Nullable Icon getIcon(@NotNull IntentionActionWithTextCaching action) {
        return action.getIcon();
      }
    };
    IntentionHintComponent.showIntentionHint(context.project(), context.file(), editor, true, intentions);
    return true;
  }

  private void executeNextStep(@NotNull ActionContext context, @NotNull @NlsContexts.Command String name, @Nullable Editor editor,
                               Callable<? extends ModCommand> supplier) {
    ModCommand next = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        return ReadAction.nonBlocking(supplier).expireWhen(context.project()::isDisposed).executeSynchronously();
      }, name, true, context.project());
    if (next == null) return;
    executeInteractively(context, next, editor);
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
    if (caret != -1) {
      editor.getCaretModel().moveToOffset(caret);
    }
    if (selectionStart != -1 && selectionEnd != -1) {
      editor.getSelectionModel().setSelection(selectionStart, selectionEnd);
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

  private static @Nullable Editor getEditor(@NotNull Project project, VirtualFile file) {
    return FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file), true);
  }

  private boolean executeComposite(@NotNull ActionContext context, ModCompositeCommand cmp, @Nullable Editor editor) {
    for (ModCommand command : cmp.commands()) {
      if (!doExecuteInteractively(context, command, editor)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void executeForFileCopy(@NotNull ModCommand command, @NotNull PsiFile file) {
    for (ModCommand cmd : command.unpack()) {
      if (cmd instanceof ModUpdateFileText updateFileText) {
        if (!updateFileText.file().equals(file.getOriginalFile().getVirtualFile())) {
          throw new UnsupportedOperationException("The command updates non-current file");
        }
        updateText(file.getProject(), file.getViewProvider().getDocument(), updateFileText);
      }
      else if (!(cmd instanceof ModNavigate) && !(cmd instanceof ModHighlight)) {
        throw new UnsupportedOperationException("Unexpected command: " + command);
      }
    }
  }

  private static void updateText(@NotNull Project project, @NotNull Document document, @NotNull ModUpdateFileText upd)
    throws IllegalStateException {
    String oldText = upd.oldText();
    if (!document.getText().equals(oldText)) {
      throw new IllegalStateException("Old text doesn't match");
    }
    List<@NotNull Fragment> ranges = calculateRanges(upd);
    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    applyRanges(document, ranges, upd.newText());
    manager.commitDocument(document);
  }

  private static boolean executeUpdate(@NotNull Project project, @NotNull ModUpdateFileText upd) {
    VirtualFile file = upd.file();
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;
    String oldText = upd.oldText();
    if (!document.getText().equals(oldText)) return false;
    List<@NotNull Fragment> ranges = calculateRanges(upd);
    return WriteAction.compute(() -> {
      applyRanges(document, ranges, upd.newText());
      PsiDocumentManager.getInstance(project).commitDocument(document);
      return true;
    });
  }

  private static void applyRanges(@NotNull Document document, List<@NotNull Fragment> ranges, String newText) {
    for (Fragment range : ranges) {
      document.replaceString(range.offset(), range.offset() + range.oldLength(),
                             newText.substring(range.offset(), range.offset() + range.newLength()));
    }
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
