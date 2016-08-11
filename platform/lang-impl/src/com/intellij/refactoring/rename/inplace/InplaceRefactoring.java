/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.rename.inplace;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
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
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Query;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 1/11/12
 */
public abstract class InplaceRefactoring {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.VariableInplaceRenamer");
  @NonNls protected static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls protected static final String OTHER_VARIABLE_NAME = "OtherVariable";
  protected static final Stack<InplaceRefactoring> ourRenamersStack = new Stack<>();
  public static final Key<InplaceRefactoring> INPLACE_RENAMER = Key.create("EditorInplaceRenamer");
  public static final Key<Boolean> INTRODUCE_RESTART = Key.create("INTRODUCE_RESTART");

  protected PsiNamedElement myElementToRename;
  protected final Editor myEditor;
  protected final Project myProject;
  protected RangeMarker myRenameOffset;
  protected String myAdvertisementText;
  private ArrayList<RangeHighlighter> myHighlighters;
  protected String myInitialName;
  protected String myOldName;
  protected RangeMarker myBeforeRevert = null;
  protected String myInsertedName;
  protected LinkedHashSet<String> myNameSuggestions;

  protected StartMarkAction myMarkAction;
  protected PsiElement myScope;

  protected RangeMarker myCaretRangeMarker;


  protected Balloon myBalloon;
  protected String myTitle;
  protected RelativePoint myTarget;

  public InplaceRefactoring(Editor editor, PsiNamedElement elementToRename, Project project) {
    this(editor, elementToRename, project, elementToRename != null ? elementToRename.getName() : null,
         elementToRename != null ? elementToRename.getName() : null);
  }

  public InplaceRefactoring(Editor editor, PsiNamedElement elementToRename, Project project, final String oldName) {
    this(editor, elementToRename, project, elementToRename != null ? elementToRename.getName() : null, oldName);
  }

  public InplaceRefactoring(
    Editor editor, PsiNamedElement elementToRename, Project project, String initialName, final String oldName) {
    myEditor = /*(editor instanceof EditorWindow)? ((EditorWindow)editor).getDelegate() : */editor;
    myElementToRename = elementToRename;
    myProject = project;
    myOldName = oldName;
    if (myElementToRename != null) {
      myInitialName = initialName;
      final PsiFile containingFile = myElementToRename.getContainingFile();
      if (!notSameFile(getTopLevelVirtualFile(containingFile.getViewProvider()), containingFile) &&
          myElementToRename != null && myElementToRename.getTextRange() != null) {
        myRenameOffset = myEditor.getDocument().createRangeMarker(myElementToRename.getTextRange());
        myRenameOffset.setGreedyToRight(true);
        myRenameOffset.setGreedyToLeft(true); // todo not sure if we need this
      }
    }
  }

  public static void unableToStartWarning(Project project, Editor editor) {
    final StartMarkAction startMarkAction = StartMarkAction.canStart(project);
    final String message = startMarkAction.getCommandName() + " is not finished yet.";
    final Document oldDocument = startMarkAction.getDocument();
    if (editor == null || oldDocument != editor.getDocument()) {
      final int exitCode = Messages.showYesNoDialog(project, message,
                                                    RefactoringBundle.getCannotRefactorMessage(null),
                                                    "Continue Started", "Cancel Started", Messages.getErrorIcon());
      navigateToStarted(oldDocument, project, exitCode);
    }
    else {
      CommonRefactoringUtil.showErrorHint(project, editor, message, RefactoringBundle.getCannotRefactorMessage(null), null);
    }
  }

  public void setAdvertisementText(String advertisementText) {
    myAdvertisementText = advertisementText;
  }


  public boolean performInplaceRefactoring(final LinkedHashSet<String> nameSuggestions) {
    myNameSuggestions = nameSuggestions;
    if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(myElementToRename)) {
      return false;
    }

    final FileViewProvider fileViewProvider = myElementToRename.getContainingFile().getViewProvider();
    VirtualFile file = getTopLevelVirtualFile(fileViewProvider);

    SearchScope referencesSearchScope = getReferencesSearchScope(file);

    final Collection<PsiReference> refs = collectRefs(referencesSearchScope);

    addReferenceAtCaret(refs);

    for (PsiReference ref : refs) {
      final PsiFile containingFile = ref.getElement().getContainingFile();

      if (notSameFile(file, containingFile)) {
        return false;
      }
    }

    final PsiElement scope = checkLocalScope();

    if (scope == null) {
      return false; // Should have valid local search scope for inplace rename
    }

    final PsiFile containingFile = scope.getContainingFile();
    if (containingFile == null) {
      return false; // Should have valid local search scope for inplace rename
    }
    //no need to process further when file is read-only
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, containingFile)) return true;

    myEditor.putUserData(INPLACE_RENAMER, this);
    ourRenamersStack.push(this);

    final List<Pair<PsiElement, TextRange>> stringUsages = new ArrayList<>();
    collectAdditionalElementsToRename(stringUsages);
    return buildTemplateAndStart(refs, stringUsages, scope, containingFile);
  }

  protected boolean notSameFile(@Nullable VirtualFile file, @NotNull PsiFile containingFile) {
    return !Comparing.equal(getTopLevelVirtualFile(containingFile.getViewProvider()), file);
  }

  protected SearchScope getReferencesSearchScope(VirtualFile file) {
    if (file == null) {
      return ProjectScope.getProjectScope(myElementToRename.getProject());
    }
    else {
      final PsiFile containingFile = myElementToRename.getContainingFile();
      if (!file.equals(containingFile.getVirtualFile())) {
        final PsiFile topLevelFile = PsiManager.getInstance(myProject).findFile(file);
        return topLevelFile == null ? ProjectScope.getProjectScope(myElementToRename.getProject()) 
                                    : new LocalSearchScope(topLevelFile);
      }
      return new LocalSearchScope(containingFile);
    }
  }

  @Nullable
  protected PsiElement checkLocalScope() {
    final SearchScope searchScope = PsiSearchHelper.SERVICE.getInstance(myElementToRename.getProject()).getUseScope(myElementToRename);
    if (searchScope instanceof LocalSearchScope) {
      final PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      return PsiTreeUtil.findCommonParent(elements);
    }

    return null;
  }

  protected abstract void collectAdditionalElementsToRename(final List<Pair<PsiElement, TextRange>> stringUsages);

  protected abstract boolean shouldSelectAll();

  protected MyLookupExpression createLookupExpression(PsiElement selectedElement) {
    return new MyLookupExpression(getInitialName(), myNameSuggestions, myElementToRename, selectedElement, shouldSelectAll(), myAdvertisementText);
  }

  protected boolean acceptReference(PsiReference reference) {
    return true;
  }

  protected Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
    final Query<PsiReference> search = ReferencesSearch.search(myElementToRename, referencesSearchScope, false);

    final CommonProcessors.CollectProcessor<PsiReference> processor = new CommonProcessors.CollectProcessor<PsiReference>() {
      @Override
      protected boolean accept(PsiReference reference) {
        return acceptReference(reference);
      }
    };

    search.forEach(processor);
    return processor.getResults();
  }

  protected boolean buildTemplateAndStart(final Collection<PsiReference> refs,
                                          final Collection<Pair<PsiElement, TextRange>> stringUsages,
                                          final PsiElement scope,
                                          final PsiFile containingFile) {
    final PsiElement context = InjectedLanguageManager.getInstance(containingFile.getProject()).getInjectionHost(containingFile);
    myScope = context != null ? context.getContainingFile() : scope;
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(myScope);

    PsiElement nameIdentifier = getNameIdentifier();
    int offset = InjectedLanguageUtil.getTopLevelEditor(myEditor).getCaretModel().getOffset();
    PsiElement selectedElement = getSelectedInEditorElement(nameIdentifier, refs, stringUsages, offset);

    boolean subrefOnPrimaryElement = false;
    boolean hasReferenceOnNameIdentifier = false;
    for (PsiReference ref : refs) {
      if (isReferenceAtCaret(selectedElement, ref)) {
        builder.replaceElement(ref.getElement(), getRangeToRename(ref), PRIMARY_VARIABLE_NAME, createLookupExpression(selectedElement), true);
        subrefOnPrimaryElement = true;
        continue;
      }
      addVariable(ref, selectedElement, builder, offset);
      hasReferenceOnNameIdentifier |= isReferenceAtCaret(nameIdentifier, ref);
    }
    if (nameIdentifier != null) {
      hasReferenceOnNameIdentifier |= selectedElement.getTextRange().contains(nameIdentifier.getTextRange());
      if (!subrefOnPrimaryElement || !hasReferenceOnNameIdentifier){
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
    catch (final StartMarkAction.AlreadyStartedException e) {
      final Document oldDocument = e.getDocument();
      if (oldDocument != myEditor.getDocument()) {
        final int exitCode = Messages.showYesNoCancelDialog(myProject, e.getMessage(), getCommandName(),
                                                            "Navigate to Started", "Cancel Started", "Cancel", Messages.getErrorIcon());
        if (exitCode == Messages.CANCEL) return true;
        navigateToAlreadyStarted(oldDocument, exitCode);
        return true;
      }
      else {

        if (!ourRenamersStack.isEmpty() && ourRenamersStack.peek() == this) {
          ourRenamersStack.pop();
          if (!ourRenamersStack.empty()) {
            myOldName = ourRenamersStack.peek().myOldName;
          }
        }

        revertState();
        final TemplateState templateState = TemplateManagerImpl.getTemplateState(InjectedLanguageUtil.getTopLevelEditor(myEditor));
        if (templateState != null) {
          templateState.gotoEnd(true);
        }
      }
      return false;
    }

    beforeTemplateStart();

    new WriteCommandAction(myProject, getCommandName()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        startTemplate(builder);
      }
    }.execute();

    if (myBalloon == null) {
      showBalloon();
    }
    return true;
  }

  protected boolean isReferenceAtCaret(PsiElement selectedElement, PsiReference ref) {
    final TextRange textRange = ref.getRangeInElement().shiftRight(ref.getElement().getTextRange().getStartOffset());
    if (selectedElement != null){
      final TextRange selectedElementRange = selectedElement.getTextRange();
      LOG.assertTrue(selectedElementRange != null, selectedElement);
      if (selectedElementRange != null && selectedElementRange.contains(textRange)) return true;
    }
    return false;
  }

  protected void beforeTemplateStart() {
    myCaretRangeMarker = myEditor.getDocument()
          .createRangeMarker(new TextRange(myEditor.getCaretModel().getOffset(), myEditor.getCaretModel().getOffset()));
    myCaretRangeMarker.setGreedyToLeft(true);
    myCaretRangeMarker.setGreedyToRight(true);
  }

  private void startTemplate(final TemplateBuilderImpl builder) {
    final Disposable disposable = Disposer.newDisposable();
    DaemonCodeAnalyzer.getInstance(myProject).disableUpdateByTimer(disposable);

    final MyTemplateListener templateListener = new MyTemplateListener() {
      @Override
      protected void restoreDaemonUpdateState() {
        Disposer.dispose(disposable);
      }
    };

    final int offset = myEditor.getCaretModel().getOffset();

    Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
    TextRange range = myScope.getTextRange();
    assert range != null;
    RangeMarker rangeMarker = topLevelEditor.getDocument().createRangeMarker(range);

    Template template = builder.buildInlineTemplate();
    template.setToShortenLongNames(false);
    template.setToReformat(false);
    myHighlighters = new ArrayList<>();
    topLevelEditor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());

    TemplateManager.getInstance(myProject).startTemplate(topLevelEditor, template, templateListener);
    restoreOldCaretPositionAndSelection(offset);
    highlightTemplateVariables(template, topLevelEditor);
  }

  private void highlightTemplateVariables(Template template, Editor topLevelEditor) {
    //add highlights
    if (myHighlighters != null) { // can be null if finish is called during testing
      Map<TextRange, TextAttributes> rangesToHighlight = new HashMap<>();
      final TemplateState templateState = TemplateManagerImpl.getTemplateState(topLevelEditor);
      if (templateState != null) {
        EditorColorsManager colorsManager = EditorColorsManager.getInstance();
        for (int i = 0; i < templateState.getSegmentsCount(); i++) {
          final TextRange segmentOffset = templateState.getSegmentRange(i);
          final String name = template.getSegmentName(i);
          TextAttributes attributes = null;
          if (name.equals(PRIMARY_VARIABLE_NAME)) {
            attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES);
          }
          else if (name.equals(OTHER_VARIABLE_NAME)) {
            attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
          }
          if (attributes == null) continue;
          rangesToHighlight.put(segmentOffset, attributes);
        }
      }
      addHighlights(rangesToHighlight, topLevelEditor, myHighlighters, HighlightManager.getInstance(myProject));
    }
  }

  private void restoreOldCaretPositionAndSelection(final int offset) {
    //move to old offset
    Runnable runnable = () -> {
      myEditor.getCaretModel().moveToOffset(restoreCaretOffset(offset));
      restoreSelection();
    };

    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    if (lookup != null && lookup.getLookupStart() <= (restoreCaretOffset(offset))) {
      lookup.setFocusDegree(LookupImpl.FocusDegree.UNFOCUSED);
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

  protected void navigateToAlreadyStarted(Document oldDocument, @Messages.YesNoResult int exitCode) {
    navigateToStarted(oldDocument, myProject, exitCode);
  }

  private static void navigateToStarted(final Document oldDocument, final Project project, @Messages.YesNoResult final int exitCode) {
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(oldDocument);
    if (file != null) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(virtualFile);
        for (FileEditor editor : editors) {
          if (editor instanceof TextEditor) {
            final Editor textEditor = ((TextEditor)editor).getEditor();
            final TemplateState templateState = TemplateManagerImpl.getTemplateState(textEditor);
            if (templateState != null) {
              if (exitCode == Messages.YES) {
                final TextRange range = templateState.getVariableRange(PRIMARY_VARIABLE_NAME);
                if (range != null) {
                  new OpenFileDescriptor(project, virtualFile, range.getStartOffset()).navigate(true);
                  return;
                }
              }
              else if (exitCode > 0){
                templateState.gotoEnd();
                return;
              }
            }
          }
        }
      }
    }
  }

  @Nullable
  protected PsiElement getNameIdentifier() {
    return myElementToRename instanceof PsiNameIdentifierOwner ? ((PsiNameIdentifierOwner)myElementToRename).getNameIdentifier() : null;
  }

  @Nullable
  protected StartMarkAction startRename() throws StartMarkAction.AlreadyStartedException {
    final StartMarkAction[] markAction = new StartMarkAction[1];
    final StartMarkAction.AlreadyStartedException[] ex = new StartMarkAction.AlreadyStartedException[1];
    CommandProcessor.getInstance().executeCommand(myProject, () -> {
      try {
        markAction[0] = StartMarkAction.start(myEditor, myProject, getCommandName());
      }
      catch (StartMarkAction.AlreadyStartedException e) {
        ex[0] = e;
      }
    }, getCommandName(), null);
    if (ex[0] != null) throw ex[0];
    return markAction[0];
  }

  @Nullable
  protected PsiNamedElement getVariable() {
    // todo we can use more specific class, shouldn't we?
    //Class clazz = myElementToRename != null? myElementToRename.getClass() : PsiNameIdentifierOwner.class; 
    if (myElementToRename != null && myElementToRename.isValid()) {
      if (Comparing.strEqual(myOldName, myElementToRename.getName())) return myElementToRename;
      if (myRenameOffset != null) return PsiTreeUtil.findElementOfClassAtRange(
        myElementToRename.getContainingFile(), myRenameOffset.getStartOffset(), myRenameOffset.getEndOffset(), PsiNameIdentifierOwner.class);
    }

    if (myRenameOffset != null) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
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

  protected void addReferenceAtCaret(Collection<PsiReference> refs) {
    PsiFile myEditorFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    // Note, that myEditorFile can be different from myElement.getContainingFile() e.g. in injections: myElement declaration in one
    // file / usage in another !
    final PsiReference reference = (myEditorFile != null ?
                                    myEditorFile : myElementToRename.getContainingFile())
      .findReferenceAt(myEditor.getCaretModel().getOffset());
    if (reference instanceof PsiMultiReference) {
      final PsiReference[] references = ((PsiMultiReference)reference).getReferences();
      for (PsiReference ref : references) {
        addReferenceIfNeeded(refs, ref);
      }
    }
    else {
      addReferenceIfNeeded(refs, reference);
    }
  }

  private void addReferenceIfNeeded(@NotNull final Collection<PsiReference> refs, @Nullable final PsiReference reference) {
    if (reference != null && reference.isReferenceTo(myElementToRename) && !refs.contains(reference)) {
      refs.add(reference);
    }
  }

  protected void showDialogAdvertisement(final String actionId) {
    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    final Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    if (shortcuts.length > 0) {
      setAdvertisementText("Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to show dialog with more options");
    }
  }

  public String getInitialName() {
    if (myInitialName == null) {
      final PsiNamedElement variable = getVariable();
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
      final Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
      ApplicationManager.getApplication().runWriteAction(() -> {
        final TemplateState state = TemplateManagerImpl.getTemplateState(topLevelEditor);
        assert state != null;
        final int segmentsCount = state.getSegmentsCount();
        final Document document = topLevelEditor.getDocument();
        for (int i = 0; i < segmentsCount; i++) {
          final TextRange segmentRange = state.getSegmentRange(i);
          document.replaceString(segmentRange.getStartOffset(), segmentRange.getEndOffset(), myOldName);
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
  protected abstract String getCommandName();

  public void finish(boolean success) {
    if (!ourRenamersStack.isEmpty() && ourRenamersStack.peek() == this) {
      ourRenamersStack.pop();
    }
    if (myHighlighters != null) {
      if (!myProject.isDisposed()) {
        final HighlightManager highlightManager = HighlightManager.getInstance(myProject);
        for (RangeHighlighter highlighter : myHighlighters) {
          highlightManager.removeSegmentHighlighter(myEditor, highlighter);
        }
      }

      myHighlighters = null;
      myEditor.putUserData(INPLACE_RENAMER, null);
    }
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
      highlightManager.addOccurrenceHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, 0, highlighters, null);
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

  private void addVariable(final PsiReference reference,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder,
                           int offset) {
    final PsiElement element = reference.getElement();
    if (element == selectedElement && checkRangeContainsOffset(offset, reference.getRangeInElement(), element)) {
      builder.replaceElement(reference.getElement(), getRangeToRename(reference), PRIMARY_VARIABLE_NAME, createLookupExpression(selectedElement), true);
    }
    else {
      builder.replaceElement(reference.getElement(), getRangeToRename(reference), OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  private void addVariable(final PsiElement element,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder) {
    addVariable(element, null, selectedElement, builder);
  }

  private void addVariable(final PsiElement element,
                           @Nullable final TextRange textRange,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder) {
    if (element == selectedElement) {
      builder.replaceElement(element, getRangeToRename(element), PRIMARY_VARIABLE_NAME, createLookupExpression(myElementToRename), true);
    }
    else if (textRange != null) {
      builder.replaceElement(element, textRange, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
    else {
      builder.replaceElement(element, getRangeToRename(element), OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  @NotNull
  protected TextRange getRangeToRename(@NotNull PsiElement element) {
    return new TextRange(0, element.getTextLength());
  }

  @NotNull
  protected TextRange getRangeToRename(@NotNull PsiReference reference) {
    return reference.getRangeInElement();
  }

  public void setElementToRename(PsiNamedElement elementToRename) {
    myElementToRename = elementToRename;
  }

  protected boolean isIdentifier(final String newName, final Language language) {
    final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(language);
    return namesValidator == null || namesValidator.isIdentifier(newName, myProject);
  }

  protected static VirtualFile getTopLevelVirtualFile(final FileViewProvider fileViewProvider) {
    VirtualFile file = fileViewProvider.getVirtualFile();
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    return file;
  }

  @TestOnly
  public static void checkCleared() {
    try {
      assert ourRenamersStack.isEmpty() : ourRenamersStack;
    }
    finally {
      ourRenamersStack.clear();
    }
  }

  private PsiElement getSelectedInEditorElement(@Nullable PsiElement nameIdentifier,
                                                final Collection<PsiReference> refs,
                                                Collection<Pair<PsiElement, TextRange>> stringUsages,
                                                final int offset) {
    //prefer reference in case of self-references
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (checkRangeContainsOffset(offset, ref.getRangeInElement(), element)) return element;
    }

    if (nameIdentifier != null) {
      final TextRange range = nameIdentifier.getTextRange();
      if (range != null && checkRangeContainsOffset(offset, range, nameIdentifier, 0)) return nameIdentifier;
    }

    for (Pair<PsiElement, TextRange> stringUsage : stringUsages) {
      if (checkRangeContainsOffset(offset, stringUsage.second, stringUsage.first)) return stringUsage.first;
    }

    LOG.error(nameIdentifier + " by " + this.getClass().getName());
    return null;
  }

  private boolean checkRangeContainsOffset(int offset, final TextRange textRange, PsiElement element) {
    return checkRangeContainsOffset(offset, textRange, element, element.getTextRange().getStartOffset());
  }

  private boolean checkRangeContainsOffset(int offset, final TextRange textRange, PsiElement element, int shiftOffset) {
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(myProject);
    final PsiLanguageInjectionHost injectionHost = injectedLanguageManager.getInjectionHost(element);
    if (injectionHost != null) {
      final PsiElement nameIdentifier = getNameIdentifier();
      final PsiLanguageInjectionHost initialInjectedHost = nameIdentifier != null ? injectedLanguageManager.getInjectionHost(nameIdentifier) : null;
      if (initialInjectedHost != null && initialInjectedHost != injectionHost) {
        return false;
      }
      return injectedLanguageManager.injectedToHost(element, textRange).shiftRight(shiftOffset).containsOffset(offset);
    }
    return textRange.shiftRight(shiftOffset).containsOffset(offset);
  }

  protected boolean isRestart() {
    final Boolean isRestart = myEditor.getUserData(INTRODUCE_RESTART);
    return isRestart != null && isRestart;
  }

  public static boolean canStartAnotherRefactoring(Editor editor, Project project, RefactoringActionHandler handler, PsiElement... element) {
    final InplaceRefactoring inplaceRefactoring = getActiveInplaceRenamer(editor);
    return StartMarkAction.canStart(project) == null ||
           (inplaceRefactoring != null && element.length == 1 && inplaceRefactoring.startsOnTheSameElement(handler, element[0]));
  }

  public static InplaceRefactoring getActiveInplaceRenamer(Editor editor) {
    return editor != null ? editor.getUserData(INPLACE_RENAMER) : null;
  }

  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return getVariable() == element;
  }

  protected void releaseResources() {
  }

  @Nullable
  protected JComponent getComponent() {
    return null;
  }

  protected void showBalloon() {
    final JComponent component = getComponent();
    if (component == null) return;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    final BalloonBuilder balloonBuilder = JBPopupFactory.getInstance().createDialogBalloonBuilder(component, null).setSmallVariant(true);
    myBalloon = balloonBuilder.createBalloon();
    Disposer.register(myProject, myBalloon);
    Disposer.register(myBalloon, new Disposable() {
      @Override
      public void dispose() {
        releaseIfNotRestart();
        myEditor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null);
      }
    });
    myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    myBalloon.show(new PositionTracker<Balloon>(myEditor.getContentComponent()) {
      @Override
      public RelativePoint recalculateLocation(Balloon object) {
        if (myTarget != null && !popupFactory.isBestPopupLocationVisible(myEditor)) {
          return myTarget;
        }
        if (myCaretRangeMarker != null && myCaretRangeMarker.isValid()) {
          myEditor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION,
                               myEditor.offsetToVisualPosition(myCaretRangeMarker.getStartOffset()));
        }
        final RelativePoint target = popupFactory.guessBestPopupLocation(myEditor);
        final Point screenPoint = target.getScreenPoint();
        int y = screenPoint.y;
        if (target.getPoint().getY() > myEditor.getLineHeight() + myBalloon.getPreferredSize().getHeight()) {
          y -= myEditor.getLineHeight();
        }
        myTarget = new RelativePoint(new Point(screenPoint.x, y));
        return myTarget;
      }
    }, Balloon.Position.above);
  }

  protected void releaseIfNotRestart() {
    if (!isRestart()) {
      releaseResources();
    }
  }

  private abstract class MyTemplateListener extends TemplateEditingAdapter {

    protected abstract void restoreDaemonUpdateState();

    @Override
    public void beforeTemplateFinished(final TemplateState templateState, Template template) {
      try {
        final TextResult value = templateState.getVariableValue(PRIMARY_VARIABLE_NAME);
        myInsertedName = value != null ? value.toString() : null;

        TextRange range = templateState.getCurrentVariableRange();
        final int currentOffset = myEditor.getCaretModel().getOffset();
        if (range == null && myRenameOffset != null) {
          range = new TextRange(myRenameOffset.getStartOffset(), myRenameOffset.getEndOffset());
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
      finally {
        restoreDaemonUpdateState();
      }
    }

    @Override
    public void templateFinished(Template template, final boolean brokenOff) {
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
            ((EditorImpl)InjectedLanguageUtil.getTopLevelEditor(myEditor)).stopDumbLater();
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
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        documentManager.commitAllDocuments();
        finish(false);
        moveOffsetAfter(false);
      }
      finally {
        try {
          restoreDaemonUpdateState();
        }
        finally {
          FinishMarkAction.finish(myProject, myEditor, myMarkAction);
        }
      }
    }
  }
}
