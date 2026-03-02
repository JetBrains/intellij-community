// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.editorActions.TabOutScopesTracker;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching;
import com.intellij.codeInsight.intention.impl.IntentionContainer;
import com.intellij.codeInsight.intention.impl.IntentionGroup;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.template.CaretAutoMoveController;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.codeInspection.ui.OptPaneUtils;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModChooseAction;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModCompositeCommand;
import com.intellij.modcommand.ModCopyToClipboard;
import com.intellij.modcommand.ModCreateFile;
import com.intellij.modcommand.ModDeleteFile;
import com.intellij.modcommand.ModDisplayMessage;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModHighlight;
import com.intellij.modcommand.ModLaunchEditorAction;
import com.intellij.modcommand.ModMoveFile;
import com.intellij.modcommand.ModNavigate;
import com.intellij.modcommand.ModNothing;
import com.intellij.modcommand.ModOpenUrl;
import com.intellij.modcommand.ModRegisterTabOut;
import com.intellij.modcommand.ModShowConflicts;
import com.intellij.modcommand.ModStartRename;
import com.intellij.modcommand.ModStartTemplate;
import com.intellij.modcommand.ModUpdateFileText;
import com.intellij.modcommand.ModUpdateFileText.Fragment;
import com.intellij.modcommand.ModUpdateReferences;
import com.intellij.modcommand.ModUpdateSystemOptions;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
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
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.Renamer;
import com.intellij.refactoring.rename.RenamerFactory;
import com.intellij.refactoring.suggested.PerformSuggestedRefactoringKt;
import com.intellij.refactoring.suggested.SuggestedRefactoringAvailability;
import com.intellij.refactoring.suggested.SuggestedRefactoringState;
import com.intellij.refactoring.suggested.SuggestedRefactoringStateChanges;
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.Icon;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

@ApiStatus.Internal
public class ModCommandExecutorImpl extends ModCommandBatchExecutorImpl {
  private static final Key<List<RangeHighlighter>> HIGHLIGHTERS_ON_NAVIGATED_ELEMENTS = Key.create("mod.command.existing.highlighters");
  
  @RequiresEdt
  @Override
  public void executeInteractively(@NotNull ActionContext context, @NotNull ModCommand command, @Nullable Editor editor) {
    if (editor != null) {
      editor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
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
    if (command instanceof ModRegisterTabOut tabOut) {
      return executeTabOut(project, tabOut, editor);
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
    if (command instanceof ModMoveFile moveFile) {
      String message = executeMove(moveFile);
      return handleError(project, editor, message);
    }
    if (command instanceof ModShowConflicts showConflicts) {
      return executeShowConflicts(context, showConflicts, editor, tail);
    }
    if (command instanceof ModEditOptions<?> options) {
      return executeEditOptions(context, options, editor);
    }
    if (command instanceof ModStartTemplate startTemplate) {
      return executeStartTemplate(context, startTemplate, editor);
    }
    if (command instanceof ModUpdateSystemOptions updateOptions) {
      return executeUpdateInspectionOptions(context, updateOptions);
    }
    if (command instanceof ModLaunchEditorAction action) {
      return executeLaunchEditorAction(project, action, editor);
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  private static boolean executeLaunchEditorAction(@NotNull Project project, @NotNull ModLaunchEditorAction action, @Nullable Editor editor) {
    AnAction anAction = ActionManager.getInstance().getAction(action.actionId());
    if (anAction == null) {
      return handleError(project, editor, AnalysisBundle.message("modcommand.executor.action.not.found", action.actionId()));
    }
    if (!(editor instanceof EditorEx ex)) return action.optional();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (ex.isDisposed()) return;
      DataContext ctx = ex.getDataContext();
      AnActionEvent event = AnActionEvent.createEvent(anAction, ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
      if (!ActionUtil.updateAction(anAction, event).isPerformed()) return;
      if (event.getPresentation().isEnabledAndVisible()) {
        ActionUtil.performAction(anAction, event);
      }
    });
    return true;
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

  private static <T extends OptionContainer> boolean executeEditOptions(@NotNull ActionContext context,
                                                                        @NotNull ModEditOptions<T> options,
                                                                        @Nullable Editor editor) {
    T container = options.containerSupplier().get();
    OptPaneUtils.editOptions(context.project(), container, options.title(), () -> {
      ModCommandExecutor.executeInteractively(context, options.title(), editor, () -> options.nextCommand().apply(container));
    });
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
    TextRange templateRange = template.fields().stream().map(ModStartTemplate.TemplateField::range)
      .reduce(TextRange::union).orElse(null);
    if (templateRange == null) return true;
    PsiElement startElement = psiFile.findElementAt(templateRange.getStartOffset());
    PsiElement endElement = psiFile.findElementAt(templateRange.getEndOffset() - 1);
    PsiElement templateElement = endElement != null && startElement != null ?
                                 requireNonNullElse(PsiTreeUtil.findCommonParent(startElement, endElement), psiFile) : psiFile;
    int templateStart = templateElement.getTextRange().getStartOffset();
    WriteAction.run(() -> {
      TemplateBuilderImpl builder = new TemplateBuilderImpl(templateElement);
      for (ModStartTemplate.TemplateField field : template.fields()) {
        switch (field) {
          case ModStartTemplate.ExpressionField(TextRange range, String varName, Expression expression) -> {
            if (varName != null) {
              builder.replaceElement(templateElement, range.shiftLeft(templateStart), varName, expression, true);
            } else {
              builder.replaceElement(templateElement, range.shiftLeft(templateStart), expression);
            }
          }
          case ModStartTemplate.DependantVariableField(TextRange range, String varName, String variableName, boolean alwaysStopAt) ->
            builder.replaceElement(templateElement, range.shiftLeft(templateStart), varName, variableName, alwaysStopAt);
          case ModStartTemplate.EndField(TextRange range) -> {
            PsiElement leaf = psiFile.findElementAt(range.getStartOffset());
            if (leaf != null) {
              builder.setEndVariableBefore(leaf);
            }
          }
        }
      }

      final Template tmpl = builder.buildInlineTemplate();
      CaretAutoMoveController.forbidCaretMovementInsideIfNeeded(finalEditor, () -> {
        finalEditor.getCaretModel().moveToOffset(templateStart);
        TemplateManager.getInstance(context.project()).startTemplate(finalEditor, tmpl, new TemplateEditingAdapter() {
          @Override
          public void templateFinished(@NotNull Template tmpl, boolean brokenOff) {
            ModCommandExecutor.executeInteractively(
              context,
              name,
              editor,
              () -> template.templateFinishFunction().apply(psiFile)
            );
          }
        });
      });
    });
    return true;
  }

  private boolean executeShowConflicts(@NotNull ActionContext context, @NotNull ModShowConflicts conflicts, @Nullable Editor editor,
                                       @NotNull ModCommand tail) {
    MultiMap<PsiElement, String> conflictData = new MultiMap<>(new LinkedHashMap<>());
    conflicts.conflicts().forEach((e, c) -> conflictData.put(e, c.messages()));
    if (conflictData.isEmpty()) return true;
    ActionContextPointer pointer = new ActionContextPointer(context);
    Project project = context.project();
    var conflictsDialog =
      new ConflictsDialog(project, conflictData, () -> {
        ActionContext restored = pointer.restoreAndCheck(editor);
        if (restored != null) {
          doExecuteInteractively(restored, tail, ModCommand.nop(), editor);
        }
      });
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
    TextRange nameRange = requireNonNullElse(rename.symbolRange().nameIdentifierRange(), range);
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
    PsiElement injectedElement = manager.findInjectedElementAt(psiFile, nameRange.getStartOffset());
    PsiElement psiElement = injectedElement != null ? injectedElement : psiFile.findElementAt(nameRange.getStartOffset());
    PsiNamedElement namedElement = PsiTreeUtil.getNonStrictParentOfType(psiElement, PsiNamedElement.class);
    if (namedElement == null) return false;
    Editor finalEditor = getEditor(project, editor, file);
    if (finalEditor == null) return false;
    if (injectedElement != null) {
      finalEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(finalEditor, injectedElement.getContainingFile()); 
    }
    DataContext context = DataManager.getInstance().getDataContext(finalEditor.getComponent());
    Set<String> nameSuggestions = new LinkedHashSet<>(rename.nameSuggestions());
    if (nameSuggestions.isEmpty() || nameSuggestions.equals(Collections.singleton(namedElement.getName()))) {
      ActionUtil.underModalProgress(project, RefactoringBundle.message("progress.title.collecting.suggested.names"),
                                    () -> NameSuggestionProvider.suggestNames(namedElement, psiElement, nameSuggestions));
    }
    DataContext finalContext = SimpleDataContext.builder()
      .setParent(context)
      .add(CommonDataKeys.PSI_ELEMENT, namedElement)
      .add(CommonDataKeys.EDITOR, finalEditor)
      .add(PsiElementRenameHandler.NAME_SUGGESTIONS, List.copyOf(nameSuggestions))
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
    ActionContextPointer pointer = new ActionContextPointer(context);
    ReadAction.nonBlocking(() -> {
      ActionContext restored = pointer.restore();
      if (restored == null) {
        return List.<ActionAndPresentation>of();
      }
      return StreamEx.of(chooser.actions()).mapToEntry(action -> action.getPresentation(restored))
        .nonNullValues().mapKeyValue(ActionAndPresentation::new).toList();
    }).finishOnUiThread(ModalityState.defaultModalityState(), actions -> {
      ActionContext restored = pointer.restoreAndCheck(editor);
      if (restored == null || actions.isEmpty()) return;

      String name = chooser.title();
      if (actions.size() == 1) {
        ModCommandAction action = actions.getFirst().action();
        ModCommandExecutor.executeInteractively(restored, name, editor, () -> {
          if (action.getPresentation(restored) == null) return ModCommand.nop();
          return action.perform(restored);
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
      IntentionHintComponent.showIntentionHint(restored.project(), restored.file(), finalEditor, true, intentions);
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

  private static boolean executeTabOut(@NotNull Project project, ModRegisterTabOut tabOut, @Nullable Editor editor) {
    VirtualFile file = actualize(tabOut.file());
    if (file == null) return false;
    int rangeStart = tabOut.rangeStart();
    int rangeEnd = tabOut.rangeEnd();
    int target = tabOut.target();

    Editor fileEditor = getEditor(project, editor, file);

    if (fileEditor == null) return false;
    WriteAction.run(() -> {
      TabOutScopesTracker.getInstance().registerScopeRange(fileEditor, rangeStart, rangeEnd, target);
    });
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
  protected @Unmodifiable @NotNull List<@NotNull Fragment> calculateRanges(@NotNull ModUpdateFileText upd) {
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

  private static class ActionContextPointer {
    private final @NotNull Project myProject;
    private final @NotNull VirtualFile myFile;
    private final @Nullable SmartPsiElementPointer<PsiElement> myElementPointer;
    private final @NotNull RangeMarker myOffsetMarker;
    private final @NotNull RangeMarker mySelectionMarker;
    private boolean myDisposed = false;

    ActionContextPointer(@NotNull ActionContext context) {
      myProject = context.project();
      myFile = requireNonNull(context.file().getVirtualFile());
      myElementPointer = context.element() != null ? SmartPointerManager.createPointer(context.element()) : null;
      Document document = context.file().getFileDocument();
      myOffsetMarker = document.createRangeMarker(context.offset(), context.offset());
      mySelectionMarker = document.createRangeMarker(context.selection());
    }

    boolean isValid() {
      if (myDisposed) throw new IllegalStateException("Already disposed");
      return myFile.isValid() &&
             (myElementPointer == null || myElementPointer.getElement() != null) &&
             myOffsetMarker.isValid() &&
             mySelectionMarker.isValid();
    }

    @Nullable ActionContext restore() {
      if (!isValid()) return null;
      PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
      if (file == null) return null;
      return new ActionContext(myProject, file, myOffsetMarker.getStartOffset(),
                               mySelectionMarker.getTextRange(), myElementPointer != null ? myElementPointer.getElement() : null);
    }

    void dispose() {
      myDisposed = true;
      myOffsetMarker.dispose();
      mySelectionMarker.dispose();
    }

    private @Nullable ActionContext restoreAndCheck(@Nullable Editor editor) {
      ActionContext restored = restore();
      dispose();
      if (restored == null) {
        if (myProject.isDisposed()) return null;
        executeMessage(myProject, new ModDisplayMessage(LangBundle.message("tooltip.unable.to.proceed.document.was.changed"),
                                                        ModDisplayMessage.MessageKind.ERROR), editor);
      }
      return restored;
    }
  }
}
