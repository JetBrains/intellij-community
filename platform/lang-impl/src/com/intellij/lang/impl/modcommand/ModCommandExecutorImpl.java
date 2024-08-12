// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInsight.intention.impl.IntentionContainer;
import com.intellij.codeInsight.intention.impl.IntentionGroup;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.*;
import com.intellij.modcommand.ModChooseMember.SelectionMode;
import com.intellij.modcommand.ModUpdateFileText.Fragment;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.Renamer;
import com.intellij.refactoring.rename.RenamerFactory;
import com.intellij.refactoring.suggested.*;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNullElse;

public class ModCommandExecutorImpl extends ModCommandBatchExecutorImpl {
  private static final Key<List<RangeHighlighter>> HIGHLIGHTERS_ON_NAVIGATED_ELEMENTS = Key.create("mod.command.existing.highlighters");
  
  @RequiresEdt
  @Override
  public void executeInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor) {
    if (editor instanceof EditorWindow window) {
      editor = window.getDelegate();
    }
    if (!ensureWritable(context.project(), command)) return;
    doExecuteInteractively(context, command, ModCommand.nop(), editor);
  }

  private boolean doExecuteInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @NotNull ModCommand tail,
                                         @Nullable Editor editor) {
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
      return executeNavigate(project, nav, editor);
    }
    if (command instanceof ModHighlight highlight) {
      return executeHighlight(project, highlight);
    }
    if (command instanceof ModCopyToClipboard copyToClipboard) {
      return executeCopyToClipboard(copyToClipboard);
    }
    if (command instanceof ModOpenUrl openUrl) {
      return executeOpenUrl(project, openUrl);
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
      return executeMessage(project, message, editor);
    }
    if (command instanceof ModStartRename rename) {
      return executeRename(project, rename, editor);
    }
    if (command instanceof ModCreateFile create) {
      String message = executeCreate(project, create);
      return handleError(project, editor, message);
    }
    if (command instanceof ModDeleteFile deleteFile) {
      String message = executeDelete(deleteFile);
      return handleError(project, editor, message);
    }
    if (command instanceof ModShowConflicts showConflicts) {
      return executeShowConflicts(context, showConflicts, editor, tail);
    }
    if (command instanceof ModStartTemplate startTemplate) {
      return executeStartTemplate(context, startTemplate, editor);
    }
    if (command instanceof ModUpdateSystemOptions updateOptions) {
      return executeUpdateInspectionOptions(context, updateOptions);
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  private static boolean handleError(@NotNull Project project, @Nullable Editor editor, @Nls String message) {
    if (message == null) return true;
    executeMessage(project, new ModDisplayMessage(message, ModDisplayMessage.MessageKind.ERROR), editor);
    return false;
  }

  private static @Nullable PsiElement findElementAtRange(PsiFile psiFile, TextRange declarationRange) {
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
      controller.setOption(option.bindId(), value);
    }
  }

  private static boolean executeChooseMember(@NotNull ActionContext context, @NotNull ModChooseMember modChooser, @Nullable Editor editor) {
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
    ModCommandExecutor.executeInteractively(context, modChooser.title(), editor, () -> modChooser.nextCommand().apply(result));
    return true;
  }

  private static boolean executeStartTemplate(@NotNull ActionContext context, @NotNull ModStartTemplate template, @Nullable Editor editor) {
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
          if (expr.varName() != null) {
            builder.replaceElement(psiFile, expr.range(), expr.varName(), expr.expression(), true);
          } else {
            builder.replaceElement(psiFile, expr.range(), expr.expression());
          }
        }
        else if (field instanceof ModStartTemplate.DependantVariableField variableField) {
          builder.replaceElement(psiFile, variableField.range(), variableField.varName(),
                                 variableField.dependantVariableName(), variableField.alwaysStopAt());
        }
        else if (field instanceof ModStartTemplate.EndField endField) {
          PsiElement leaf = psiFile.findElementAt(endField.range().getStartOffset());
          if (leaf != null) {
            builder.setEndVariableBefore(leaf);
          }
        }
      }

      final Template tmpl = builder.buildInlineTemplate();
      finalEditor.getCaretModel().moveToOffset(0);
      TemplateManager.getInstance(context.project()).startTemplate(finalEditor, tmpl, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(@NotNull Template tmpl, boolean brokenOff) {
          ModCommandExecutor.executeInteractively(context, name, editor, () -> template.templateFinishFunction().apply(psiFile));
        }
      });
    });
    return true;
  }

  private boolean executeShowConflicts(@NotNull ActionContext context, @NotNull ModShowConflicts conflicts, @Nullable Editor editor,
                                       @NotNull ModCommand tail) {
    MultiMap<PsiElement, String> conflictData = new MultiMap<>(new LinkedHashMap<>());
    conflicts.conflicts().forEach((e, c) -> conflictData.put(e, c.messages()));
    if (conflictData.isEmpty()) return true;
    var conflictsDialog =
      new ConflictsDialog(context.project(), conflictData, () -> doExecuteInteractively(context, tail, ModCommand.nop(), editor));
    return conflictsDialog.showAndGet();
  }

  private static boolean executeCopyToClipboard(@NotNull ModCopyToClipboard clipboard) {
    CopyPasteManager.getInstance().setContents(new StringSelection(clipboard.content()));
    return true;
  }

  private static boolean executeOpenUrl(@NotNull Project project, @NotNull ModOpenUrl openUrl) {
    BrowserUtil.browse(openUrl.url(), project);
    return true;
  }

  private static boolean executeRename(@NotNull Project project, @NotNull ModStartRename rename, @Nullable Editor editor) {
    VirtualFile file = actualize(rename.file());
    if (file == null) return false;
    PsiFile psiFile = PsiManagerEx.getInstanceEx(project).findFile(file);
    if (psiFile == null) return false;
    TextRange range = rename.symbolRange().range();
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
    PsiElement injectedElement = manager.findInjectedElementAt(psiFile, range.getStartOffset());
    PsiElement psiElement = injectedElement != null ? injectedElement : psiFile.findElementAt(range.getStartOffset());
    PsiNamedElement namedElement = PsiTreeUtil.getNonStrictParentOfType(psiElement, PsiNamedElement.class);
    if (namedElement == null) return false;
    Editor finalEditor = getEditor(project, editor, file);
    if (finalEditor == null) return false;
    if (injectedElement != null) {
      finalEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(finalEditor, injectedElement.getContainingFile()); 
    }
    DataContext context = DataManager.getInstance().getDataContext(finalEditor.getComponent());
    DataContext finalContext = SimpleDataContext.builder()
      .setParent(context)
      .add(CommonDataKeys.PSI_ELEMENT, namedElement)
      .add(CommonDataKeys.EDITOR, finalEditor)
      .add(PsiElementRenameHandler.NAME_SUGGESTIONS, rename.nameSuggestions())
      .build();
    PsiElement anchor = namedElement instanceof PsiNameIdentifierOwner owner ? 
                        requireNonNullElse(owner.getNameIdentifier(), namedElement) : namedElement;
    finalEditor.getSelectionModel().removeSelection();
    finalEditor.getCaretModel().moveToOffset(anchor.getTextOffset());
    Renamer renamer = RenamerFactory.EP_NAME.getExtensionList().stream().flatMap(factory -> factory.createRenamers(finalContext).stream())
      .findFirst().orElse(null);
    if (renamer == null) return false;
    renamer.performRename();
    return true;
  }

  private static @Nullable Editor getEditor(@NotNull Project project, @Nullable Editor editor, @NotNull VirtualFile file) {
    if (editor == null) return getEditor(project, file);
    VirtualFile editorVirtualFile = editor.getVirtualFile(); // EditorComboBox and similar components might provide no virtual file
    if (editorVirtualFile != null &&
        !file.equals(editorVirtualFile) && !editor.getDocument().equals(FileDocumentManager.getInstance().getDocument(file))) {
      return getEditor(project, file);
    }
    else {
      return editor;
    }
  }

  private static final class ErrorInTestException extends RuntimeException {
    private ErrorInTestException(String message) {
      super(message);
    }
  }

  private static boolean executeMessage(@NotNull Project project, @NotNull ModDisplayMessage message, @Nullable Editor editor) {
    if (editor == null) {
      editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) return false;
    }
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

  private static boolean executeChoose(@NotNull ActionContext context, ModChooseAction chooser, @Nullable Editor editor) {
    record ActionAndPresentation(@NotNull ModCommandAction action, @NotNull Presentation presentation) {}
    VirtualFile file = context.file().getVirtualFile();
    if (file == null) return false;
    Editor finalEditor = editor == null ? getEditor(context.project(), file) : editor;
    if (finalEditor == null) return false;
    ReadAction.nonBlocking(() -> {
      return StreamEx.of(chooser.actions()).mapToEntry(action -> action.getPresentation(context))
        .nonNullValues().mapKeyValue(ActionAndPresentation::new).toList();
    }).finishOnUiThread(ModalityState.defaultModalityState(), actions -> {
      if (actions.isEmpty()) return;

      String name = chooser.title();
      if (actions.size() == 1) {
        ModCommandAction action = actions.get(0).action();
        ModCommandExecutor.executeInteractively(context, name, editor, () -> {
          if (action.getPresentation(context) == null) return ModCommand.nop();
          return action.perform(context);
        });
        return;
      }
      List<IntentionActionWithTextCaching> actionsWithTextCaching = ContainerUtil.map(
        actions, (actionAndPresentation) -> {
          IntentionAction intention = new ModCommandActionWrapper(actionAndPresentation.action(), actionAndPresentation.presentation());
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
      IntentionHintComponent.showIntentionHint(context.project(), context.file(), finalEditor, true, intentions);
    }).submit(AppExecutorUtil.getAppExecutorService());
    return true;
  }

  private static boolean executeNavigate(@NotNull Project project, ModNavigate nav, @Nullable Editor editor) {
    VirtualFile file = actualize(nav.file());
    if (file == null) return false;
    int selectionStart = nav.selectionStart();
    int selectionEnd = nav.selectionEnd();
    int caret = nav.caret();

    Editor fileEditor = getEditor(project, editor, file);

    if (fileEditor == null) return false;
    if (caret != -1) {
      fileEditor.getCaretModel().moveToOffset(caret);
      fileEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
    if (selectionStart != -1 && selectionEnd != -1) {
      fileEditor.getSelectionModel().setSelection(selectionStart, selectionEnd);
    }
    return true;
  }

  private static boolean executeHighlight(Project project, ModHighlight highlight) {
    VirtualFile file = actualize(highlight.file());
    if (file == null) return false;
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
    if (!(fileEditor instanceof TextEditor textEditor)) return false;
    Editor editor = textEditor.getEditor();
    HighlightManager manager = HighlightManager.getInstance(project);
    List<RangeHighlighter> existingHighlighters = editor.getUserData(HIGHLIGHTERS_ON_NAVIGATED_ELEMENTS);
    if (existingHighlighters != null) {
      for (RangeHighlighter highlighter : existingHighlighters) {
        manager.removeSegmentHighlighter(editor, highlighter);
      }
    }
    ArrayList<RangeHighlighter> addedHighlighters = new ArrayList<>();
    for (ModHighlight.HighlightInfo info : highlight.highlights()) {
      manager.addRangeHighlight(editor, info.range().getStartOffset(), info.range().getEndOffset(), info.attributesKey(),
                                 info.hideByTextChange(), addedHighlighters);
    }
    editor.putUserData(HIGHLIGHTERS_ON_NAVIGATED_ELEMENTS, addedHighlighters);
    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
    return true;
  }

  private static @Nullable Editor getEditor(@NotNull Project project, VirtualFile file) {
    return FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file), true);
  }

  private boolean executeComposite(@NotNull ActionContext context, ModCompositeCommand cmp, @Nullable Editor editor) {
    List<@NotNull ModCommand> commands = cmp.commands();
    int size = commands.size();
    for (int i = 0; i < size; i++) {
      ModCommand command = commands.get(i);
      if (!doExecuteInteractively(context, command, new ModCompositeCommand(commands.subList(i + 1, size)), editor)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected @NotNull List<@NotNull Fragment> calculateRanges(@NotNull ModUpdateFileText upd) {
    List<@NotNull Fragment> ranges = upd.updatedRanges();
    if (!ranges.isEmpty()) return ranges;
    String oldText = upd.oldText();
    String newText = upd.newText();
    List<DiffFragment> fragments = ComparisonManager.getInstance().compareChars(oldText, newText, ComparisonPolicy.DEFAULT,
                                                                                DumbProgressIndicator.INSTANCE);
    return ContainerUtil.map(fragments, fr -> new Fragment(fr.getStartOffset2(), fr.getEndOffset1() - fr.getStartOffset1(),
                                                           fr.getEndOffset2() - fr.getStartOffset2()));
  }

  @Override
  protected @NotNull String getFileNamePresentation(Project project, VirtualFile file) {
    String fileTitle = EditorTabPresentationUtil.getCustomEditorTabTitle(project, file);
    return fileTitle != null ? fileTitle : super.getFileNamePresentation(project, file);
  }
}
