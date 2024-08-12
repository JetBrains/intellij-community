// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.DottedBorder;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Query;
import com.intellij.util.containers.NotNullList;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.List;
import java.util.*;

public abstract class InplaceRefactoring {
  protected static final Logger LOG = Logger.getInstance(VariableInplaceRenamer.class);
  protected static final @NonNls String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  protected static final @NonNls String OTHER_VARIABLE_NAME = "OtherVariable";
  public static final Key<Boolean> INPLACE_RENAME_ALLOWED = Key.create("EditorInplaceRenameAllowed");
  public static final Key<InplaceRefactoring> INPLACE_RENAMER = Key.create("EditorInplaceRenamer");
  public static final Key<Boolean> INTRODUCE_RESTART = Key.create("INTRODUCE_RESTART");
  private static boolean ourShowBalloonInHeadlessMode;

  protected PsiNamedElement myElementToRename;
  protected final Editor myEditor;
  protected final Project myProject;
  protected RangeMarker myRenameOffset;
  protected @NlsContexts.PopupAdvertisement String myAdvertisementText;
  private ArrayList<RangeHighlighter> myHighlighters;
  protected String myInitialName;
  protected String myOldName;
  protected RangeMarker myBeforeRevert;
  protected String myInsertedName;
  protected LinkedHashSet<String> myNameSuggestions;

  protected StartMarkAction myMarkAction;
  protected PsiElement myScope;

  protected RangeMarker myCaretRangeMarker;


  protected Balloon myBalloon;
  protected @NlsContexts.Command String myTitle;
  protected RelativePoint myTarget;

  public InplaceRefactoring(@NotNull Editor editor,
                            @Nullable PsiNamedElement elementToRename,
                            @NotNull Project project) {
    this(editor, elementToRename, project, elementToRename != null ? elementToRename.getName() : null,
         elementToRename != null ? elementToRename.getName() : null);
  }

  public InplaceRefactoring(@NotNull Editor editor,
                            @Nullable PsiNamedElement elementToRename,
                            @NotNull Project project,
                            @Nullable String oldName) {
    this(editor, elementToRename, project, elementToRename != null ? elementToRename.getName() : null, oldName);
  }

  public InplaceRefactoring(@NotNull Editor editor,
                            @Nullable PsiNamedElement elementToRename,
                            @NotNull Project project,
                            @Nullable String initialName,
                            @Nullable String oldName) {
    myEditor = /*(editor instanceof EditorWindow)? ((EditorWindow)editor).getDelegate() : */editor;
    myElementToRename = elementToRename;
    myProject = project;
    myOldName = oldName;
    if (myElementToRename != null) {
      myInitialName = initialName;
      PsiFile containingFile = myElementToRename.getContainingFile();
      FileViewProvider viewProvider = containingFile.getViewProvider();
      if (!notSameFile(getTopLevelVirtualFile(viewProvider), containingFile)
          && myElementToRename.getTextRange() != null) {
        myRenameOffset = myEditor.getDocument().createRangeMarker(myElementToRename.getTextRange());
        myRenameOffset.setGreedyToRight(true);
        myRenameOffset.setGreedyToLeft(true);
      }
    }
  }

  public static void unableToStartWarning(Project project, @NotNull Editor editor) {
    StartMarkAction startMarkAction = StartMarkAction.canStart(editor);
    String message = IdeBundle.message("dialog.message.command.not.finished.yet", startMarkAction.getCommandName());
    Document oldDocument = startMarkAction.getDocument();
    if (oldDocument != editor.getDocument()) {
      int exitCode = Messages.showYesNoDialog(project, message,
                                                    RefactoringBundle.getCannotRefactorMessage(null),
                                                    RefactoringBundle.message("inplace.refactoring.continue.started"),
                                                    RefactoringBundle.message("inplace.refactoring.abandon.started"), Messages.getErrorIcon());
      navigateToStarted(oldDocument, project, exitCode, startMarkAction.getCommandName());
    }
    else {
      CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.getCannotRefactorMessage(null), null);
    }
  }

  public void setAdvertisementText(@NlsContexts.PopupAdvertisement String advertisementText) {
    myAdvertisementText = advertisementText;
  }


  public boolean performInplaceRefactoring(@Nullable LinkedHashSet<String> nameSuggestions) {
    if (myEditor instanceof ImaginaryEditor && myEditor.getUserData(INPLACE_RENAME_ALLOWED) != Boolean.TRUE) return false;
    myNameSuggestions = nameSuggestions;
    if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(myElementToRename)) {
      return false;
    }

    FileViewProvider fileViewProvider = myElementToRename.getContainingFile().getViewProvider();
    VirtualFile file = getTopLevelVirtualFile(fileViewProvider);

    SearchScope referencesSearchScope = getReferencesSearchScope(file);

    Collection<PsiReference> references = ProgressManager.getInstance().runProcessWithProgressSynchronously(() ->
      ReadAction.compute(() -> {
        Collection<PsiReference> refs = collectRefs(referencesSearchScope);

        addReferenceAtCaret(refs);
        return refs;
      })
    , RefactoringBundle.message("progress.title.collecting.references"), true, myProject);

    if (references == null) return false;

    for (PsiReference ref : references) {
      PsiFile containingFile = ref.getElement().getContainingFile();

      if (notSameFile(file, containingFile)) {
        return false;
      }
    }

    PsiElement scope = checkLocalScope();

    if (scope == null) {
      return false; // Should have valid local search scope for inplace rename
    }

    PsiFile containingFile = scope.getContainingFile();
    if (containingFile == null) {
      return false; // Should have valid local search scope for inplace rename
    }
    //no need to process further when file is read-only
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, containingFile)) return true;

    myEditor.putUserData(INPLACE_RENAMER, this);

    List<Pair<PsiElement, TextRange>> stringUsages = new NotNullList<>();
    collectAdditionalElementsToRename(stringUsages);
    try {
      return buildTemplateAndStart(references, stringUsages, scope, containingFile);
    }
    catch (Throwable e) {
      myEditor.putUserData(INPLACE_RENAMER, null);
      FinishMarkAction.finish(myProject, myEditor, myMarkAction);
      throw e;
    }
  }

  protected boolean notSameFile(@Nullable VirtualFile file, @NotNull PsiFile containingFile) {
    return !Comparing.equal(getTopLevelVirtualFile(containingFile.getViewProvider()), file);
  }

  protected SearchScope getReferencesSearchScope(VirtualFile file) {
    if (file == null) {
      return ProjectScope.getProjectScope(myElementToRename.getProject());
    }
    else {
      PsiFile containingFile = myElementToRename.getContainingFile();
      if (!file.equals(containingFile.getVirtualFile())) {
        PsiFile topLevelFile = PsiManager.getInstance(myProject).findFile(file);
        return topLevelFile == null ? ProjectScope.getProjectScope(myElementToRename.getProject())
                                    : new LocalSearchScope(topLevelFile);
      }
      return new LocalSearchScope(containingFile);
    }
  }

  protected @Nullable PsiElement checkLocalScope() {
    SearchScope searchScope = PsiSearchHelper.getInstance(myElementToRename.getProject()).getUseScope(myElementToRename);
    if (searchScope instanceof LocalSearchScope) {
      PsiElement[] elements = getElements((LocalSearchScope)searchScope);
      return PsiTreeUtil.findCommonParent(elements);
    }

    return null;
  }

  private PsiElement @NotNull [] getElements(LocalSearchScope searchScope) {
    PsiElement[] elements = searchScope.getScope();
    FileViewProvider provider = myElementToRename.getContainingFile().getViewProvider();
    for (PsiElement element : elements) {
      if (!(element instanceof PsiFile) || ((PsiFile)element).getViewProvider() != provider) {
        return elements;
      }
    }
    return new PsiElement[] { myElementToRename.getContainingFile() };
  }

  protected void collectAdditionalElementsToRename(@NotNull List<? super Pair<PsiElement, TextRange>> stringUsages) {}

  protected abstract boolean shouldSelectAll();

  protected MyLookupExpression createLookupExpression(PsiElement selectedElement) {
    return new MyLookupExpression(getInitialName(), myNameSuggestions, myElementToRename, selectedElement, shouldSelectAll(), myAdvertisementText);
  }

  protected Expression createTemplateExpression(PsiElement selectedElement) {
    return createLookupExpression(selectedElement);
  }

  protected boolean acceptReference(PsiReference reference) {
    return true;
  }

  protected Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
    Query<PsiReference> search = ReferencesSearch.search(myElementToRename, referencesSearchScope, false);

    CommonProcessors.CollectProcessor<PsiReference> processor = new CommonProcessors.CollectProcessor<>() {
      @Override
      protected boolean accept(PsiReference reference) {
        ProgressManager.checkCanceled();
        return acceptReference(reference);
      }
    };

    search.forEach(processor);
    return processor.getResults();
  }

  protected boolean buildTemplateAndStart(@NotNull Collection<PsiReference> refs,
                                          @NotNull Collection<Pair<PsiElement, TextRange>> stringUsages,
                                          @NotNull PsiElement scope,
                                          @NotNull PsiFile containingFile) {
    PsiElement context = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
    Editor topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(myEditor);
    //do not change scope if it's injected fragment in separate editor (created by QuickEditAction)
    //in this case there is no top level editor and there would be no need to adjust ranges
    myScope = context == null || topLevelEditor == myEditor ? scope : context.getContainingFile();
    TemplateBuilderImpl builder = new TemplateBuilderImpl(myScope);

    PsiElement nameIdentifier = getNameIdentifier();
    int offset = topLevelEditor.getCaretModel().getOffset();
    PsiElement selectedElement = getSelectedInEditorElement(nameIdentifier, refs, stringUsages, offset);

    boolean subrefOnPrimaryElement = false;
    boolean hasReferenceOnNameIdentifier = false;
    for (PsiReference ref : refs) {
      if (isReferenceAtCaret(selectedElement, ref, offset)) {
        Expression expression = createTemplateExpression(selectedElement);
        builder.replaceElement(ref.getElement(), getRangeToRename(ref), PRIMARY_VARIABLE_NAME, expression,
                               shouldStopAtLookupExpression(expression));
        subrefOnPrimaryElement = true;
        continue;
      }
      addVariable(ref, selectedElement, builder, offset);
      hasReferenceOnNameIdentifier |= isReferenceAtCaret(nameIdentifier, ref);
    }
    if (nameIdentifier != null) {
      hasReferenceOnNameIdentifier |= selectedElement.getTextRange().contains(nameIdentifier.getTextRange());
      if (!subrefOnPrimaryElement || !hasReferenceOnNameIdentifier) {
        addVariable(nameIdentifier, selectedElement, builder);
      }
    }
    for (Pair<PsiElement, TextRange> usage : stringUsages) {
      addVariable(usage.first, usage.second, selectedElement, builder);
    }
    addAdditionalVariables(builder);

    int segmentsLimit = Registry.intValue("inplace.rename.segments.limit", -1);
    if (segmentsLimit != -1 && builder.getElementsCount() > segmentsLimit) {
      return false;
    }

    try {
      myMarkAction = startRename();
    }
    catch (StartMarkAction.AlreadyStartedException e) {
      Document oldDocument = e.getDocument();
      if (oldDocument != myEditor.getDocument()) {
        int exitCode = Messages.showYesNoCancelDialog(myProject, e.getMessage(), getCommandName(),
                                                            RefactoringBundle.message("inplace.refactoring.navigate.to.started"),
                                                            RefactoringBundle.message("inplace.refactoring.abandon.started"),
                                                            RefactoringBundle.message("inplace.refactoring.cancel.current"), Messages.getErrorIcon());
        navigateToAlreadyStarted(oldDocument, exitCode);
        return true;
      }
      else {
        revertState();
        TemplateState templateState = TemplateManagerImpl.getTemplateState(topLevelEditor);
        if (templateState != null) {
          templateState.gotoEnd(true);
        }
      }
      return false;
    }

    if (myBalloon == null) {
      showBalloon();
    }

    beforeTemplateStart();

    WriteCommandAction.writeCommandAction(myProject).withName(getCommandName()).run(() -> startTemplate(builder));

    afterTemplateStart();

    return true;
  }

  protected boolean shouldStopAtLookupExpression(Expression expression) {
    return expression instanceof MyLookupExpression;
  }

  /**
   * Checks if selected element contains reference range and covers current offset as well
   */
  protected boolean isReferenceAtCaret(PsiElement selectedElement, PsiReference ref, int offset) {
    return isReferenceAtCaret(selectedElement, ref) && 
           checkRangeContainsOffset(offset, ref.getRangeInElement(), ref.getElement());
  }

  protected boolean isReferenceAtCaret(PsiElement selectedElement, PsiReference ref) {
    TextRange textRange = ref.getRangeInElement().shiftRight(ref.getElement().getTextRange().getStartOffset());
    if (selectedElement != null){
      TextRange selectedElementRange = selectedElement.getTextRange();
      LOG.assertTrue(selectedElementRange != null, selectedElement);
      if (selectedElementRange.contains(textRange)) return true;
    }
    return false;
  }

  protected void beforeTemplateStart() {
    myCaretRangeMarker =
      myEditor.getDocument().createRangeMarker(myEditor.getCaretModel().getOffset(), myEditor.getCaretModel().getOffset());
    myCaretRangeMarker.setGreedyToLeft(true);
    myCaretRangeMarker.setGreedyToRight(true);
  }

  protected void afterTemplateStart(){
  }

  private void startTemplate(TemplateBuilderImpl builder) {
    MyTemplateListener templateListener = new MyTemplateListener();

    int offset = myEditor.getCaretModel().getOffset();

    Editor topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(myEditor);
    TextRange range = myScope.getTextRange();
    assert range != null;
    RangeMarker rangeMarker = topLevelEditor.getDocument().createRangeMarker(range);

    Template template = builder.buildInlineTemplate();
    template.setToShortenLongNames(false);
    template.setToReformat(false);
    myHighlighters = new ArrayList<>();
    topLevelEditor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());

    TemplateManager.getInstance(myProject).startTemplate(topLevelEditor, template, templateListener);
    restoreOldCaretPositionAndSelection(myEditor, restoreCaretOffset(offset), this::restoreSelection);
    highlightTemplateVariables(template, topLevelEditor);

    TemplateState templateState = TemplateManagerImpl.getTemplateState(topLevelEditor);
    if (templateState != null) {
      DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(templateState);
    }
  }

  private void highlightTemplateVariables(Template template, Editor topLevelEditor) {
    //add highlights
    if (myHighlighters != null) { // can be null if finish is called during testing
      TemplateState templateState = TemplateManagerImpl.getTemplateState(topLevelEditor);
      Map<TextRange, TextAttributes> rangesToHighlight;
      if (templateState == null) {
        rangesToHighlight = Collections.emptyMap();
      }
      else {
        EditorColorsManager colorsManager = EditorColorsManager.getInstance();
        rangesToHighlight = new HashMap<>();
        variableHighlights(template, templateState).forEach((range, attributesKey) -> {
          TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(attributesKey);
          if (attributes != null) {
            TextAttributesWithKey attributesWithKey = new TextAttributesWithKey(attributes, attributesKey);
            rangesToHighlight.put(range, attributesWithKey);
          }
        });
      }
      addHighlights(rangesToHighlight, topLevelEditor, myHighlighters, HighlightManager.getInstance(myProject));
    }
  }

  private static @NotNull Map<TextRange, TextAttributesKey> variableHighlights(@NotNull Template template, @NotNull TemplateState templateState) {
    Map<TextRange, TextAttributesKey> rangesToHighlight = new HashMap<>();
    for (int i = 0; i < templateState.getSegmentsCount(); i++) {
      TextRange segmentOffset = templateState.getSegmentRange(i);
      String name = template.getSegmentName(i);
      TextAttributesKey attributesKey;
      if (name.equals(PRIMARY_VARIABLE_NAME)) {
        attributesKey = EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES;
      }
      else if (name.equals(OTHER_VARIABLE_NAME)) {
        attributesKey = EditorColors.SEARCH_RESULT_ATTRIBUTES;
      }
      else {
        continue;
      }
      rangesToHighlight.put(segmentOffset, attributesKey);
    }
    return rangesToHighlight;
  }

  static void restoreOldCaretPositionAndSelection(@NotNull Editor editor,
                                                  int restoredCaretOffset,
                                                  @NotNull Runnable restoreSelection) {
    //move to old offset
    Runnable runnable = () -> {
      editor.getCaretModel().moveToOffset(restoredCaretOffset);
      restoreSelection.run();
    };

    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup != null && lookup.getLookupStart() <= restoredCaretOffset) {
      lookup.setLookupFocusDegree(LookupFocusDegree.UNFOCUSED);
      lookup.performGuardedChange(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected void restoreSelection() {
  }

  protected int restoreCaretOffset(int offset) {
    return myCaretRangeMarker.isValid() ? myCaretRangeMarker.getEndOffset() : offset;
  }

  public void stopIntroduce(Editor editor) {
    stopIntroduce(editor, myProject, getCommandName());
  }

  public static void stopIntroduce(Editor editor, Project project, @NlsContexts.Command String commandName) {
    TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null) {
      Runnable runnable = () -> templateState.gotoEnd(true);
      CommandProcessor.getInstance().executeCommand(project, runnable, commandName, commandName);
    }
  }

  protected void navigateToAlreadyStarted(Document oldDocument, int exitCode) {
    finish(true);
    if (exitCode != Messages.CANCEL) {
      navigateToStarted(oldDocument, myProject, exitCode, getCommandName());
    }
  }

  private static void navigateToStarted(Document oldDocument,
                                        Project project,
                                        @Messages.YesNoResult int exitCode,
                                        @NlsContexts.Command String commandName) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(oldDocument);
    if (file != null) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile instanceof VirtualFileWindow) {
        virtualFile = ((VirtualFileWindow)virtualFile).getDelegate();
      }
      if (virtualFile != null) {
        FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(virtualFile);
        for (FileEditor editor : editors) {
          if (editor instanceof TextEditor) {
            Editor textEditor = ((TextEditor)editor).getEditor();
            TemplateState templateState = TemplateManagerImpl.getTemplateState(textEditor);
            if (templateState != null) {
              if (exitCode == Messages.YES) {
                TextRange range = templateState.getVariableRange(PRIMARY_VARIABLE_NAME);
                if (range != null) {
                  PsiNavigationSupport.getInstance().createNavigatable(project, virtualFile, range.getStartOffset())
                                      .navigate(true);
                  return;
                }
              }
              else if (exitCode > 0){
                stopIntroduce(textEditor, project, commandName);
                return;
              }
            }
          }
        }
      }
    }
  }

  protected @Nullable PsiElement getNameIdentifier() {
    return myElementToRename instanceof PsiNameIdentifierOwner ? ((PsiNameIdentifierOwner)myElementToRename).getNameIdentifier() : null;
  }

  public static EditorEx createPreviewComponent(Project project, FileType languageFileType) {
    Document document = EditorFactory.getInstance().createDocument("");
    UndoUtil.disableUndoFor(document);
    EditorEx previewEditor = (EditorEx)EditorFactory.getInstance().createEditor(document, project, languageFileType, true);
    previewEditor.setOneLineMode(true);
    EditorSettings settings = previewEditor.getSettings();
    settings.setAdditionalLinesCount(0);
    settings.setAdditionalColumnsCount(1);
    settings.setRightMarginShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setLineNumbersShown(false);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setVirtualSpace(false);
    previewEditor.setHorizontalScrollbarVisible(false);
    previewEditor.setVerticalScrollbarVisible(false);
    previewEditor.setCaretEnabled(false);
    settings.setLineCursorWidth(1);

    Color bg = previewEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
    previewEditor.setBackgroundColor(bg);
    previewEditor.setBorder(BorderFactory.createCompoundBorder(new DottedBorder(JBColor.GRAY), new LineBorder(bg, 2)));

    return previewEditor;
  }

  protected @Nullable StartMarkAction startRename() throws StartMarkAction.AlreadyStartedException {
    return startMarkAction(myProject, myEditor, getCommandName());
  }

  static @NotNull StartMarkAction startMarkAction(
    @NotNull Project project,
    @NotNull Editor editor,
    @NlsContexts.Command String commandName
  ) throws StartMarkAction.AlreadyStartedException {
    StartMarkAction[] markAction = new StartMarkAction[1];
    StartMarkAction.AlreadyStartedException[] ex = new StartMarkAction.AlreadyStartedException[1];
    CommandProcessor.getInstance().executeCommand(project, () -> {
      try {
        markAction[0] = StartMarkAction.start(editor, project, commandName);
      }
      catch (StartMarkAction.AlreadyStartedException e) {
        ex[0] = e;
      }
    }, commandName, null);
    if (ex[0] != null) throw ex[0];
    return markAction[0];
  }

  protected @Nullable PsiNamedElement getVariable() {
    // todo we can use more specific class, shouldn't we?
    //Class clazz = myElementToRename != null? myElementToRename.getClass() : PsiNameIdentifierOwner.class;
    if (myElementToRename != null && myElementToRename.isValid()) {
      if (Comparing.strEqual(myOldName, myElementToRename.getName())) return myElementToRename;
      if (myRenameOffset != null) return PsiTreeUtil.findElementOfClassAtRange(
        myElementToRename.getContainingFile(), myRenameOffset.getStartOffset(), myRenameOffset.getEndOffset(), PsiNameIdentifierOwner.class);
    }

    if (myRenameOffset != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
      if (psiFile != null) {
        return PsiTreeUtil.findElementOfClassAtRange(psiFile, myRenameOffset.getStartOffset(), myRenameOffset.getEndOffset(), PsiNameIdentifierOwner.class);
      }
    }
    return myElementToRename;
  }

  /**
   * Called after the completion of the refactoring, either a successful or a failed one.
   *
   * @param success true if the refactoring was accepted, false if it was cancelled (by undo or Esc)
   */
  protected void moveOffsetAfter(boolean success) {
    if (myCaretRangeMarker != null) {
      myCaretRangeMarker.dispose();
    }
  }

  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
  }

  protected void addReferenceAtCaret(Collection<? super PsiReference> refs) {
    PsiFile myEditorFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    // Note, that myEditorFile can be different from myElement.getContainingFile() e.g. in injections: myElement declaration in one
    // file / usage in another !
    PsiReference reference = (myEditorFile != null ?
                                    myEditorFile : myElementToRename.getContainingFile())
      .findReferenceAt(myEditor.getCaretModel().getOffset());
    if (reference instanceof PsiMultiReference) {
      PsiReference[] references = ((PsiMultiReference)reference).getReferences();
      for (PsiReference ref : references) {
        addReferenceIfNeeded(refs, ref);
      }
    }
    else {
      addReferenceIfNeeded(refs, reference);
    }
  }

  private void addReferenceIfNeeded(@NotNull Collection<? super PsiReference> refs, @Nullable PsiReference reference) {
    if (reference != null && reference.isReferenceTo(myElementToRename) && !refs.contains(reference)) {
      refs.add(reference);
    }
  }

  protected void showDialogAdvertisement(@NonNls @Nullable String actionId) {
    Shortcut shortcut = KeymapUtil.getPrimaryShortcut(actionId);
    if (shortcut != null) {
      setAdvertisementText(RefactoringBundle.message("inplace.refactoring.advertisement.text", KeymapUtil.getShortcutText(shortcut)));
    }
  }

  public String getInitialName() {
    if (myInitialName == null) {
      PsiNamedElement variable = getVariable();
      if (variable != null) {
        return variable.getName();
      }
      LOG.error("Initial name should be provided");
      return "";
    }
    return myInitialName;
  }

  protected void revertState() {
    if (myOldName == null) return;
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      Editor topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(myEditor);
      ApplicationManager.getApplication().runWriteAction(() -> {
        TemplateState state = TemplateManagerImpl.getTemplateState(topLevelEditor);
        if (state != null) {
          int segmentsCount = state.getSegmentsCount();
          Document document = topLevelEditor.getDocument();
          for (int i = 0; i < segmentsCount; i++) {
            TextRange segmentRange = state.getSegmentRange(i);
            document.replaceString(segmentRange.getStartOffset(), segmentRange.getEndOffset(), myOldName);
          }
        }
      });
      if (!myProject.isDisposed() && myProject.isOpen()) {
        PsiDocumentManager.getInstance(myProject).commitDocument(topLevelEditor.getDocument());
      }
    }, getCommandName(), null);
  }

  /**
   * Returns the name of the command performed by the refactoring.
   *
   * @return command name
   */
  protected abstract @NlsContexts.Command String getCommandName();

  public void finish(boolean success) {
    if (myHighlighters != null) {
      if (!myProject.isDisposed()) {
        HighlightManager highlightManager = HighlightManager.getInstance(myProject);
        for (RangeHighlighter highlighter : myHighlighters) {
          highlightManager.removeSegmentHighlighter(myEditor, highlighter);
        }
      }

      myHighlighters = null;
    }
    myEditor.putUserData(INPLACE_RENAMER, null);
    if (myBalloon != null) {
           if (!isRestart()) {
             myBalloon.hide();
           }
         }
  }

  protected void addHighlights(@NotNull Map<TextRange, TextAttributes> ranges,
                               @NotNull Editor editor,
                               @NotNull Collection<RangeHighlighter> highlighters,
                               @NotNull HighlightManager highlightManager) {
    for (Map.Entry<TextRange, TextAttributes> entry : ranges.entrySet()) {
      TextRange range = entry.getKey();
      TextAttributes attributes = entry.getValue();
      if (attributes instanceof TextAttributesWithKey) {
        TextAttributesKey attributesKey = ((TextAttributesWithKey)attributes).getTextAttributesKey();
        highlightManager.addOccurrenceHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributesKey, 0, highlighters);
      }
      else {
        highlightManager.addOccurrenceHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, 0, highlighters, null);
      }
    }

    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
  }

  protected abstract boolean performRefactoring();

  /**
   * if brokenOff but not canceled
   */
  protected void performCleanup() {}

  private void addVariable(PsiReference reference,
                           PsiElement selectedElement,
                           TemplateBuilderImpl builder,
                           int offset) {
    PsiElement element = reference.getElement();
    if (element == selectedElement && checkRangeContainsOffset(offset, reference.getRangeInElement(), element)) {
      Expression expression = createTemplateExpression(selectedElement);
      builder.replaceElement(reference.getElement(), getRangeToRename(reference), PRIMARY_VARIABLE_NAME, expression,
                             shouldStopAtLookupExpression(expression));
    }
    else {
      builder.replaceElement(reference.getElement(), getRangeToRename(reference), OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  private void addVariable(PsiElement element,
                           PsiElement selectedElement,
                           TemplateBuilderImpl builder) {
    addVariable(element, null, selectedElement, builder);
  }

  private void addVariable(PsiElement element,
                           @Nullable TextRange textRange,
                           PsiElement selectedElement,
                           TemplateBuilderImpl builder) {
    if (element == selectedElement) {
      Expression expression = createTemplateExpression(myElementToRename);
      builder.replaceElement(element, getRangeToRename(element), PRIMARY_VARIABLE_NAME, expression, shouldStopAtLookupExpression(expression));
    }
    else {
      builder.replaceElement(element, Objects.requireNonNullElseGet(textRange, () -> getRangeToRename(element)), OTHER_VARIABLE_NAME,
                             PRIMARY_VARIABLE_NAME, false);
    }
  }

  protected @NotNull TextRange getRangeToRename(@NotNull PsiElement element) {
    return new TextRange(0, element.getTextLength());
  }

  protected @NotNull TextRange getRangeToRename(@NotNull PsiReference reference) {
    return reference.getRangeInElement();
  }

  public void setElementToRename(PsiNamedElement elementToRename) {
    myElementToRename = elementToRename;
  }

  protected boolean isIdentifier(String newName, Language language) {
    return LanguageNamesValidation.isIdentifier(language, newName, myProject);
  }

  protected static @NotNull VirtualFile getTopLevelVirtualFile(@NotNull FileViewProvider fileViewProvider) {
    VirtualFile file = fileViewProvider.getVirtualFile();
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    return file;
  }

  protected PsiElement getSelectedInEditorElement(@Nullable PsiElement nameIdentifier,
                                                  @NotNull Collection<? extends PsiReference> refs,
                                                  @NotNull Collection<? extends Pair<PsiElement, TextRange>> stringUsages,
                                                  int offset) {
    //prefer reference in case of self-references
    for (PsiReference ref : refs) {
      PsiElement element = ref.getElement();
      if (checkRangeContainsOffset(offset, ref.getRangeInElement(), element)) return element;
    }

    if (nameIdentifier != null) {
      TextRange range = nameIdentifier.getTextRange();
      if (range != null && checkRangeContainsOffset(offset, range, nameIdentifier, 0)) return nameIdentifier;
    }

    for (Pair<PsiElement, TextRange> stringUsage : stringUsages) {
      if (checkRangeContainsOffset(offset, stringUsage.second, stringUsage.first)) return stringUsage.first;
    }

    LOG.error(nameIdentifier + " by " + this.getClass().getName());
    return null;
  }

  private boolean checkRangeContainsOffset(int offset, TextRange textRange, PsiElement element) {
    return checkRangeContainsOffset(offset, textRange, element, element.getTextRange().getStartOffset());
  }

  protected boolean checkRangeContainsOffset(int offset, TextRange textRange, PsiElement element, int shiftOffset) {
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
    PsiLanguageInjectionHost injectionHost = injectedLanguageManager.getInjectionHost(element);
    if (injectionHost != null) {
      PsiElement nameIdentifier = getNameIdentifier();
      PsiLanguageInjectionHost initialInjectedHost = nameIdentifier != null ? injectedLanguageManager.getInjectionHost(nameIdentifier) : null;
      if (initialInjectedHost != null && initialInjectedHost != injectionHost) {
        return false;
      }
      return injectedLanguageManager.injectedToHost(element, textRange.shiftRight(shiftOffset)).containsOffset(offset);
    }
    return textRange.shiftRight(shiftOffset).containsOffset(offset);
  }

  protected boolean isRestart() {
    Boolean isRestart = myEditor.getUserData(INTRODUCE_RESTART);
    return isRestart != null && isRestart;
  }

  public static boolean canStartAnotherRefactoring(@NotNull Editor editor, RefactoringActionHandler handler, PsiElement... element) {
    InplaceRefactoring inplaceRefactoring = getActiveInplaceRenamer(editor);
    return StartMarkAction.canStart(editor) == null ||
           (inplaceRefactoring != null && inplaceRefactoring.startsOnTheSameElements(editor, handler, element));
  }

  public static InplaceRefactoring getActiveInplaceRenamer(Editor editor) {
    return editor != null ? editor.getUserData(INPLACE_RENAMER) : null;
  }

  protected boolean startsOnTheSameElements(Editor editor, RefactoringActionHandler handler,
                                            PsiElement[] element) {
    return element.length == 1 && startsOnTheSameElement(handler, element[0]);
  }

  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return getVariable() == element;
  }

  protected void releaseResources() {
  }

  protected @Nullable JComponent getComponent() {
    return null;
  }

  protected void showBalloon() {
    JComponent component = getComponent();
    if (component == null) return;
    Dimension size = component.getPreferredSize();
    if (size.height == 0 && size.width == 0) return;
    if (!ourShowBalloonInHeadlessMode && ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createDialogBalloonBuilder(component, null).setSmallVariant(true);

    Color borderColor = UIManager.getColor("InplaceRefactoringPopup.borderColor");
    if (borderColor != null) {
      balloonBuilder.setBorderColor(borderColor);
    }
    adjustBalloon(balloonBuilder);

    myBalloon = balloonBuilder.createBalloon();
    Disposer.register(myProject, myBalloon);
    Disposer.register(myBalloon, () -> {
      releaseIfNotRestart();
      myEditor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null);
    });
    EditorUtil.disposeWithEditor(myEditor, myBalloon);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    showBalloonInEditor();
  }

  protected void showBalloonInEditor() {
    JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    myBalloon.show(new PositionTracker<>(myEditor.getContentComponent()) {
      @Override
      public RelativePoint recalculateLocation(@NotNull Balloon object) {
        if (myTarget != null && !popupFactory.isBestPopupLocationVisible(myEditor)) {
          return myTarget;
        }
        if (myCaretRangeMarker != null && myCaretRangeMarker.isValid()) {
          myEditor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION,
                               myEditor.offsetToVisualPosition(myCaretRangeMarker.getStartOffset()));
        }
        RelativePoint target = popupFactory.guessBestPopupLocation(myEditor);
        Point screenPoint = target.getScreenPoint();
        int y = screenPoint.y;
        if (target.getPoint().getY() > myEditor.getLineHeight() + myBalloon.getPreferredSize().getHeight()) {
          y -= myEditor.getLineHeight();
        }
        myTarget = new RelativePoint(new Point(screenPoint.x, y));
        return myTarget;
      }
    }, Balloon.Position.above);
  }

  protected void adjustBalloon(BalloonBuilder builder) {
  }

  protected void releaseIfNotRestart() {
    if (!isRestart()) {
      releaseResources();
    }
  }

  public static boolean isShowBalloonInHeadlessMode() {
    return ourShowBalloonInHeadlessMode;
  }

  public static void setShowBalloonInHeadlessMode(boolean showBalloonInHeadlessMode) {
    ourShowBalloonInHeadlessMode = showBalloonInHeadlessMode;
  }

  public static @NotNull @NlsContexts.PopupAdvertisement String getPopupOptionsAdvertisement(){
    Shortcut shortcut = KeymapUtil.getPrimaryShortcut("SelectVirtualTemplateElement");
    if (shortcut != null) {
      return RefactoringBundle.message("inplace.refactoring.tab.advertisement.text", KeymapUtil.getShortcutText(shortcut));
    }
    else {
      String enterShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM);
      String tabShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE);
      return LangBundle.message("popup.advertisement.press.or.to.replace", enterShortcut, tabShortcut);
    }
  }

  protected final void initPopupOptionsAdvertisement() {
    setAdvertisementText(getPopupOptionsAdvertisement());
  }

  private static final class TextAttributesWithKey extends TextAttributes {
    private final @NotNull TextAttributesKey myTextAttributesKey;

    TextAttributesWithKey(@NotNull TextAttributes textAttributes, @NotNull TextAttributesKey textAttributesKey) {
      myTextAttributesKey = textAttributesKey;
      copyFrom(textAttributes);
    }

    @NotNull TextAttributesKey getTextAttributesKey() {
      return myTextAttributesKey;
    }
  }

  private final class MyTemplateListener extends TemplateEditingAdapter {
    @Override
    public void beforeTemplateFinished(@NotNull TemplateState templateState, Template template) {
      TextResult value = templateState.getVariableValue(PRIMARY_VARIABLE_NAME);
      myInsertedName = value != null ? value.toString().trim() : null;

      TextRange range = templateState.getCurrentVariableRange();
      int currentOffset = myEditor.getCaretModel().getOffset();
      if (range == null && myRenameOffset != null) {
        range = myRenameOffset.getTextRange();
      }
      myBeforeRevert =
        range != null && range.getEndOffset() >= currentOffset && range.getStartOffset() <= currentOffset
        ? myEditor.getDocument().createRangeMarker(range.getStartOffset(), currentOffset)
        : null;
      if (myBeforeRevert != null) {
        myBeforeRevert.setGreedyToRight(true);
      }
      finish(true);
    }

    @Override
    public void templateFinished(@NotNull Template template, boolean brokenOff) {
      boolean bind = false;
      try {
        if (!brokenOff) {
          bind = performRefactoring();
        } else {
          performCleanup();
        }
        moveOffsetAfter(!brokenOff);
      }
      finally {
        if (!bind) {
          try {
            Editor editor = InjectedLanguageEditorUtil.getTopLevelEditor(myEditor);
            if (editor instanceof EditorImpl) {
              ((EditorImpl)editor).stopDumbLater();
            }
          }
          finally {
            FinishMarkAction.finish(myProject, myEditor, myMarkAction);
            if (myBeforeRevert != null) {
              myBeforeRevert.dispose();
            }
          }
        }
      }
    }

    @Override
    public void templateCancelled(Template template) {
      try {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        documentManager.commitAllDocuments();
        finish(false);
        moveOffsetAfter(false);
      }
      finally {
        FinishMarkAction.finish(myProject, myEditor, myMarkAction);
      }
    }
  }
}
