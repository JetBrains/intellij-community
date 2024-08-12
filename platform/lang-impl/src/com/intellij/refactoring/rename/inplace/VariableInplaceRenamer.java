// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModUpdateFileText;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.rename.*;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VariableInplaceRenamer extends InplaceRefactoring {
  public static final LanguageExtension<ResolveSnapshotProvider> INSTANCE = new LanguageExtension<>(
    "com.intellij.rename.inplace.resolveSnapshotProvider"
  );
  private ResolveSnapshotProvider.ResolveSnapshot mySnapshot;
  private TextRange mySelectedRange;
  protected Language myLanguage;
  protected @Nullable SuggestedNameInfo mySuggestedNameInfo;
  private int myOrigOffset;
  private ModUpdateFileText myRevertCommand;
  private @Nullable PsiNamedElement myElementInCopy;

  public VariableInplaceRenamer(@NotNull PsiNamedElement elementToRename,
                                @NotNull Editor editor) {
    this(elementToRename, editor, elementToRename.getProject());
  }

  public VariableInplaceRenamer(@Nullable PsiNamedElement elementToRename,
                                @NotNull Editor editor,
                                @NotNull Project project) {
    this(elementToRename, editor, project, elementToRename != null ? elementToRename.getName() : null,
         elementToRename != null ? elementToRename.getName() : null);
  }

  public VariableInplaceRenamer(@Nullable PsiNamedElement elementToRename,
                                @NotNull Editor editor,
                                @NotNull Project project,
                                @Nullable String initialName,
                                @Nullable String oldName) {
    super(editor, elementToRename, project, initialName, oldName);
  }

  @Override
  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return super.startsOnTheSameElement(handler, element) && handler instanceof VariableInplaceRenameHandler;
  }

  public boolean performInplaceRename() {
    return performInplaceRename(null);
  }

  public boolean performInplaceRename(@Nullable Collection<String> nameSuggestions) {
    final String refactoringId = getRefactoringId();
    PsiNamedElement elementToRename = getVariable();
    if (refactoringId != null) {
      final RefactoringEventData beforeData = new RefactoringEventData();
      beforeData.addElement(elementToRename);
      beforeData.addStringProperties(myOldName);
      myProject.getMessageBus()
        .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(refactoringId, beforeData);
    }
    return performInplaceRefactoring(nameSuggestions == null ? null : new LinkedHashSet<>(nameSuggestions));
  }

  @Override
  protected void collectAdditionalElementsToRename(final @NotNull List<? super Pair<PsiElement, TextRange>> stringUsages) {
    processDefaultAdditionalElementsToRename(pair -> {
      stringUsages.add(pair);
      return true;
    });
  }

  protected final boolean processDefaultAdditionalElementsToRename(@NotNull Processor<? super Pair<PsiElement, TextRange>> stringUsages) {
    final String stringToSearch = myElementToRename.getName();
    if (!StringUtil.isEmptyOrSpaces(stringToSearch)) {
      final PsiFile currentFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
      return TextOccurrencesUtil.processUsagesInStringsAndComments(
        myElementToRename, GlobalSearchScope.projectScope(myElementToRename.getProject()),
        stringToSearch, false, (psiElement, textRange) -> {
          if (psiElement.getContainingFile() == currentFile) {
            if (!stringUsages.process(Pair.create(psiElement, textRange))) {
              return false;
            }
          }
          return true;
        });
    }
    return true;
  }

  @Override
  protected boolean buildTemplateAndStart(final @NotNull Collection<PsiReference> refs,
                                          @NotNull Collection<Pair<PsiElement, TextRange>> stringUsages,
                                          final @NotNull PsiElement scope,
                                          final @NotNull PsiFile containingFile) {
    PsiFile fileCopy = (PsiFile)containingFile.copy();
    try {
      myElementInCopy = PsiTreeUtil.findSameElementInCopy(myElementToRename, fileCopy);
    }
    catch (IllegalStateException | UnsupportedOperationException e) {
      // Unsupported synthetic element?
      myElementInCopy = null;
    }
    if (appendAdditionalElement(refs, stringUsages)) {
      return super.buildTemplateAndStart(refs, stringUsages, scope, containingFile);
    }
    else {
      final RenameChooser renameChooser = new RenameChooser(myEditor) {
        @Override
        protected void runRenameTemplate(Collection<Pair<PsiElement, TextRange>> stringUsages) {
          if (!VariableInplaceRenamer.super.buildTemplateAndStart(refs, stringUsages, scope, containingFile)) {
            VariableInplaceRenameHandler.performDialogRename(myElementToRename,
                                                             myEditor,
                                                             DataManager.getInstance().getDataContext(myEditor.getContentComponent()),
                                                             myInitialName);
          }
        }
      };
      renameChooser.showChooser(refs, stringUsages);
    }
    return true;
  }

  protected boolean appendAdditionalElement(Collection<PsiReference> refs, Collection<Pair<PsiElement, TextRange>> stringUsages) {
    return stringUsages.isEmpty() || StartMarkAction.canStart(myEditor) != null;
  }

  protected boolean shouldCreateSnapshot() {
    return true;
  }

  protected String getRefactoringId() {
    return "refactoring.rename";
  }

  @Override
  protected void beforeTemplateStart() {
    super.beforeTemplateStart();
    myOrigOffset = myCaretRangeMarker.getStartOffset();
    myLanguage = myScope.getLanguage();
    if (shouldCreateSnapshot()) {
      final ResolveSnapshotProvider resolveSnapshotProvider = INSTANCE.forLanguage(myLanguage);
      mySnapshot = resolveSnapshotProvider != null ? resolveSnapshotProvider.createSnapshot(myScope) : null;
    }

    final SelectionModel selectionModel = myEditor.getSelectionModel();
    mySelectedRange =
      selectionModel.hasSelection() ? new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()) : null;
  }

  @Override
  protected void restoreSelection() {
    if (mySelectedRange != null) {
      restoreSelection(myEditor, mySelectedRange);
    }
    else if (!shouldSelectAll()) {
      myEditor.getSelectionModel().removeSelection();
    }
  }

  /**
   * @param selectedRange range which is relative to the {@code editor}
   */
   public static void restoreSelection(@NotNull Editor editor, @NotNull TextRange selectedRange) {
    if (handleSelectionIntersection(editor, selectedRange)) {
      return;
    }
    editor.getSelectionModel().setSelection(selectedRange.getStartOffset(), selectedRange.getEndOffset());
  }

  private static boolean handleSelectionIntersection(@NotNull Editor editor, @NotNull TextRange selectedRange) {
    if (editor instanceof EditorWindow editorWindow) {
      Editor hostEditor = editorWindow.getDelegate();
      PsiFile injected = editorWindow.getInjectedFile();
      TextRange hostSelectedRange = InjectedLanguageManager.getInstance(hostEditor.getProject()).injectedToHost(injected, selectedRange);
      return doHandleSelectionIntersection(hostEditor, hostSelectedRange);
    }
    else {
      return doHandleSelectionIntersection(editor, selectedRange);
    }
  }

  private static boolean doHandleSelectionIntersection(@NotNull Editor editor, @NotNull TextRange selectedRange) {
    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state == null) {
      return false;
    }
    for (int i = 0; i < state.getSegmentsCount(); i++) {
      final TextRange segmentRange = state.getSegmentRange(i);
      TextRange intersection = segmentRange.intersection(selectedRange);
      if (intersection == null) {
        continue;
      }
      editor.getSelectionModel().setSelection(intersection.getStartOffset(), intersection.getEndOffset());
      return true;
    }
    return false;
  }

  @Override
  protected int restoreCaretOffset(int offset) {
    return restoreCaretOffset(myCaretRangeMarker, offset);
  }

  static int restoreCaretOffset(@NotNull RangeMarker caretRangeMarker, int offset) {
    if (caretRangeMarker.isValid()) {
      if (caretRangeMarker.getStartOffset() <= offset && caretRangeMarker.getEndOffset() >= offset) {
        return offset;
      }
      return caretRangeMarker.getEndOffset();
    }
    return offset;
  }

  @Override
  protected boolean shouldSelectAll() {
    if (myEditor.getSettings().isPreselectRename()) return true;
    final Boolean selectAll = myEditor.getUserData(RenameHandlerRegistry.SELECT_ALL);
    return selectAll != null && selectAll.booleanValue();
  }

  protected @NotNull VariableInplaceRenamer createInplaceRenamerToRestart(PsiNamedElement variable, Editor editor, String initialName) {
    return new VariableInplaceRenamer(variable, editor, myProject, initialName, myOldName);
  }

  protected void performOnInvalidIdentifier(final String newName, final LinkedHashSet<String> nameSuggestions) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      tryRollback();
      return;
    }
    JBPopupFactory.getInstance()
      .createConfirmation(LangBundle.message("popup.title.inserted.identifier.valid"), IdeBundle.message("label.continue.editing"),
                          CommonBundle.getCancelButtonText(),
                          () -> {
                            startDumbIfPossible();
                            try {
                              tryRollback();
                              final PsiNamedElement variable = getVariable();
                              if (variable != null) {
                                createInplaceRenamerToRestart(variable, myEditor, newName).performInplaceRefactoring(nameSuggestions);
                              }
                            }
                            finally {
                              stopDumbLaterIfPossible();
                            }
                          },
                          () -> tryRollback(), 0).showInBestPositionFor(myEditor);
  }

  /**
   * @return unresolvable collision if it's desired to display it to the user; null if refactoring can proceed without additional question
   */
  protected @Nullable UnresolvableCollisionUsageInfo findCollision() {
    if (myInsertedName.equals(myOldName)) return null;
    PsiNamedElement variable = myElementInCopy;
    if (variable == null) return null;
    String newName = myInsertedName;
    List<UsageInfo> result = new ArrayList<>();
    RenamePsiElementProcessor.forElement(variable).findCollisions(variable, newName, Map.of(variable, newName), result);
    return ContainerUtil.findInstance(result, UnresolvableCollisionUsageInfo.class);
  }
  
  private @Nullable UnresolvableRenameProblem findProblem() {
    if (!isIdentifier(myInsertedName, myLanguage)) {
      return new IllegalIdentifierProblem();
    }
    UnresolvableCollisionUsageInfo collision = findCollision();
    if (collision != null) {
      return new Collision(collision);
    }
    return null;
  }

  private sealed interface UnresolvableRenameProblem {
    void showUI();
  }
  
  private final class IllegalIdentifierProblem implements UnresolvableRenameProblem {
    @Override
    public void showUI() {
      performOnInvalidIdentifier(myInsertedName, myNameSuggestions);
    }
  }

  private final class Collision implements UnresolvableRenameProblem {
    private final @NotNull UnresolvableCollisionUsageInfo collision;

    private Collision(@NotNull UnresolvableCollisionUsageInfo collision) { this.collision = collision; }

    @Override
    public void showUI() {
      RangeHighlighter highlighter = highlightConflictingElement(collision.getElement());
      String description = StringUtil.stripHtml(collision.getDescription(), false);
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        throw new BaseRefactoringProcessor.ConflictsInTestsException(List.of(description));
      }
      JBPopupFactory.getInstance().createConfirmation(
          description, IdeBundle.message("label.refactor.anyway"),
          IdeBundle.message("label.continue.editing"),
          () -> {
            if (highlighter != null) {
              highlighter.dispose();
            }
            performRefactoringRename(myInsertedName, myMarkAction);
          },
          () -> {
            if (highlighter != null) {
              highlighter.dispose();
            }
            startDumbIfPossible();
            try {
              tryRollback();
              PsiNamedElement var = getVariable();
              if (var != null) {
                createInplaceRenamerToRestart(var, myEditor, myInsertedName).performInplaceRefactoring(myNameSuggestions);
              }
            }
            finally {
              stopDumbLaterIfPossible();
            }
          },
          0)
        .showInBestPositionFor(myEditor);
    }
  }

  protected final void tryRollback() {
    if (myRevertCommand == null) return;
    Document document = InjectedLanguageEditorUtil.getTopLevelEditor(myEditor).getDocument();
    PsiFile psiFile = Objects.requireNonNull(PsiDocumentManager.getInstance(myProject).getPsiFile(document));
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, psiFile), myRevertCommand);
    }, getCommandName(), null);
    myEditor.getCaretModel().moveToOffset(myOrigOffset);
  }

  private @Nullable RangeHighlighter highlightConflictingElement(PsiElement conflictingElement) {
    if (conflictingElement != null) {
      TextRange range = conflictingElement.getTextRange();
      if (conflictingElement instanceof PsiNameIdentifierOwner owner) {
        PsiElement identifier = owner.getNameIdentifier();
        if (identifier != null) {
          range = identifier.getTextRange();
        }
      }
      List<RangeHighlighter> highlighters = new ArrayList<>();
      HighlightManager.getInstance(myProject).addRangeHighlight(
        myEditor, range.getStartOffset(), range.getEndOffset(), EditorColors.SEARCH_RESULT_ATTRIBUTES, false, true, highlighters);
      return ContainerUtil.getOnlyItem(highlighters);
    }
    return null;
  }

  protected void renameSynthetic(String newName) {
  }

  @Override
  protected MyLookupExpression createLookupExpression(PsiElement selectedElement) {
    MyLookupExpression lookupExpression =
      new MyLookupExpression(getInitialName(), myNameSuggestions, myElementToRename, selectedElement, shouldSelectAll(),
                             myAdvertisementText);
    mySuggestedNameInfo = lookupExpression.getSuggestedNameInfo();
    return lookupExpression;
  }

  protected void performRefactoringRename(String newName, StartMarkAction markAction) {
    final String refactoringId = getRefactoringId();
    try {
      PsiNamedElement elementToRename = getVariable();
      if (!isIdentifier(newName, myLanguage)) {
        return;
      }
      if (elementToRename != null) {
        WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).run(() -> renameSynthetic(newName));
      }
      for (AutomaticRenamerFactory renamerFactory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
        if (elementToRename != null && isRenamerFactoryApplicable(renamerFactory, elementToRename)) {
          final List<UsageInfo> usages = new ArrayList<>();
          final AutomaticRenamer renamer =
            renamerFactory.createRenamer(elementToRename, newName, new ArrayList<>());
          if (renamer.hasAnythingToRename()) {
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              final AutomaticRenamingDialog renamingDialog = new AutomaticRenamingDialog(myProject, renamer);
              if (!renamingDialog.showAndGet()) {
                continue;
              }
            }

            final Runnable runnable = () -> ApplicationManager.getApplication().runReadAction(() -> renamer.findUsages(usages, false, false));

            if (!ProgressManager.getInstance()
              .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject)) {
              return;
            }

            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, PsiUtilCore.toPsiElementArray(renamer.getElements()))) return;
            final ThrowableRunnable<RuntimeException> performAutomaticRename = () -> {
              CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
              MultiMap<PsiElement, UsageInfo> classified = RenameProcessor.classifyUsages(renamer.getElements(), usages);
              for (final PsiNamedElement element : renamer.getElements()) {
                final String newElementName = renamer.getNewName(element);
                if (newElementName != null) {
                  final Collection<UsageInfo> infos = classified.get(element);
                  RenameUtil.doRename(element, newElementName, infos.toArray(UsageInfo.EMPTY_ARRAY), myProject, RefactoringElementListener.DEAF);
                }
              }
            };
            if (ApplicationManager.getApplication().isUnitTestMode()) {
              WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).run(performAutomaticRename);
            } else {
              ApplicationManager.getApplication().invokeLater(() -> WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).run(performAutomaticRename));
            }
          }
        }
      }
    }
    finally {
      if (mySuggestedNameInfo != null) mySuggestedNameInfo.nameChosen(newName);
      if (refactoringId != null) {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(getVariable());
        afterData.addStringProperties(newName);
        myProject.getMessageBus()
          .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, afterData);
      }

      try {
        stopDumbLaterIfPossible();
      }
      finally {
        FinishMarkAction.finish(myProject, myEditor, markAction);
      }
    }
  }

  void startDumbIfPossible() {
    if (InjectedLanguageEditorUtil.getTopLevelEditor(myEditor) instanceof EditorImpl editor) {
      editor.startDumb();
    }
  }

  protected void stopDumbLaterIfPossible() {
    if (InjectedLanguageEditorUtil.getTopLevelEditor(myEditor) instanceof EditorImpl editor) {
      editor.stopDumbLater();
    }
  }

  protected boolean isRenamerFactoryApplicable(@NotNull AutomaticRenamerFactory renamerFactory,
                                               @NotNull PsiNamedElement elementToRename) {
    return renamerFactory.isApplicable(elementToRename);
  }

  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("renaming.command.name", myInitialName);
  }

  @Override
  protected boolean performRefactoring() {
    boolean bind = false;
    if (myInsertedName != null) {
      final CommandProcessor commandProcessor = CommandProcessor.getInstance();
      if (commandProcessor.getCurrentCommand() != null && getVariable() != null) {
        commandProcessor.setCurrentCommandName(getCommandName());
      }

      bind = true;
      ReadAction.nonBlocking(() -> findProblem())
        .expireWhen(() -> myEditor.isDisposed() || myProject.isDisposed())
        .finishOnUiThread(ModalityState.nonModal(), problem -> {
          if (problem == null) {
            if (mySnapshot != null) {
              ApplicationManager.getApplication().runWriteAction(() -> mySnapshot.apply(myInsertedName));
            }
            performRefactoringRename(myInsertedName, myMarkAction);
          }
          else {
            problem.showUI();
            cancel();
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
    }
    return bind;
  }

  private void cancel() {
    try {
      stopDumbLaterIfPossible();
    }
    finally {
      FinishMarkAction.finish(myProject, myEditor, myMarkAction);
    }
  }

  @Override
  public void finish(boolean success) {
    super.finish(success);
    if (success) {
      revertStateOnFinish();
    }
    else {
      stopDumbLaterIfPossible();
    }
  }

  @Override
  protected void performCleanup() {
    tryRollback();
  }

  private static @NotNull ModUpdateFileText getRevertModCommand(@NotNull Editor editor, String oldName) {
    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    int segmentsCount = state == null ? 0 : state.getSegmentsCount();
    Document document = editor.getDocument();
    String oldText = document.getText();
    StringBuilder newText = new StringBuilder();
    int lastPos = 0;
    List<ModUpdateFileText.Fragment> fragments = new ArrayList<>();
    for (int i = 0; i < segmentsCount; i++) {
      TextRange segmentRange = state.getSegmentRange(i);
      newText.append(oldText, lastPos, segmentRange.getStartOffset());
      fragments.add(new ModUpdateFileText.Fragment(newText.length(), segmentRange.getLength(), oldName.length()));
      newText.append(oldName);
      lastPos = segmentRange.getEndOffset();
    }
    newText.append(oldText, lastPos, oldText.length());
    return new ModUpdateFileText(editor.getVirtualFile(), oldText, newText.toString(), fragments);
  }

  protected void revertStateOnFinish() {
    myRevertCommand = getRevertModCommand(InjectedLanguageEditorUtil.getTopLevelEditor(myEditor), myOldName);
  }
}