/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * User: anna
 * Date: 1/11/12
 */
public abstract class InplaceRefactoring {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.VariableInplaceRenamer");
  @NonNls protected static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls protected static final String OTHER_VARIABLE_NAME = "OtherVariable";
  protected static final Stack<InplaceRefactoring> ourRenamersStack = new Stack<InplaceRefactoring>();
  public static final Key<InplaceRefactoring> INPLACE_RENAMER = Key.create("EditorInplaceRenamer");
  protected PsiNamedElement myElementToRename;
  protected final Editor myEditor;
  protected final Project myProject;
  protected RangeMarker myRenameOffset;
  private String myAdvertisementText;
  private ArrayList<RangeHighlighter> myHighlighters;
  protected String myInitialName;
  protected final String myOldName;
  protected RangeMarker myBeforeRevert = null;
  protected String myInsertedName;
  protected LinkedHashSet<String> myNameSuggestions;

  protected StartMarkAction myMarkAction;
  protected PsiElement myScope;
  
  private RangeMarker myCaretRangeMarker;

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
      if (!notSameFile(getTopLevelVirtualFile(containingFile.getViewProvider()), containingFile)) {
        myRenameOffset = myElementToRename != null && myElementToRename.getTextRange() != null ? myEditor.getDocument()
          .createRangeMarker(myElementToRename.getTextRange()) : null;
      }
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

    final List<Pair<PsiElement, TextRange>> stringUsages = new ArrayList<Pair<PsiElement, TextRange>>();
    collectAdditionalElementsToRename(stringUsages);
    return buildTemplateAndStart(refs, stringUsages, scope, containingFile);
  }

  protected boolean notSameFile(@Nullable VirtualFile file, PsiFile containingFile) {
    return getTopLevelVirtualFile(containingFile.getViewProvider()) != file;
  }

  protected SearchScope getReferencesSearchScope(VirtualFile file) {
    return file == null || ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file)
           ? ProjectScope.getProjectScope(myElementToRename.getProject())
           : new LocalSearchScope(myElementToRename.getContainingFile());
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

  protected abstract LookupElement[] createLookupItems(LookupElement[] lookupItems, String name);

  protected Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
    return ReferencesSearch.search(myElementToRename, referencesSearchScope, false).findAll();
  }

  protected boolean buildTemplateAndStart(final Collection<PsiReference> refs,
                                          final Collection<Pair<PsiElement, TextRange>> stringUsages,
                                          final PsiElement scope,
                                          final PsiFile containingFile) {
    final PsiElement context = containingFile.getContext();
    myScope = context != null ? context.getContainingFile() : scope;
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(myScope);

    PsiElement nameIdentifier = getNameIdentifier();
    int offset = myEditor.getCaretModel().getOffset();
    PsiElement selectedElement = getSelectedInEditorElement(nameIdentifier, refs, stringUsages, offset);

    if (nameIdentifier != null) addVariable(nameIdentifier, selectedElement, builder);
    for (PsiReference ref : refs) {
      addVariable(ref, selectedElement, builder, offset);
    }
    for (Pair<PsiElement, TextRange> usage : stringUsages) {
      addVariable(usage.first, usage.second, selectedElement, builder);
    }
    addAdditionalVariables(builder);
    try {
      myMarkAction = startRename();
    }
    catch (final StartMarkAction.AlreadyStartedException e) {
      final Document oldDocument = e.getDocument();
      if (oldDocument != myEditor.getDocument()) {
        final int exitCode = Messages.showOkCancelDialog(myProject, e.getMessage(), getCommandName(),
                                                         "Navigate to continue", "Cancel started", Messages.getErrorIcon());
        if (exitCode == -1) return true;
        navigateToAlreadyStarted(oldDocument, exitCode);
        return true;
      }
      else {
        revertState();
      }
      return false;
    }

    beforeTemplateStart();

    new WriteCommandAction(myProject, getCommandName()) {
      @Override
      protected void run(com.intellij.openapi.application.Result result) throws Throwable {
        startTemplate(builder);
      }
    }.execute();
    return true;
  }

  protected void beforeTemplateStart() {
    myCaretRangeMarker = myEditor.getDocument()
          .createRangeMarker(new TextRange(myEditor.getCaretModel().getOffset(), myEditor.getCaretModel().getOffset()));
  }

  private void startTemplate(final TemplateBuilderImpl builder) {

    final DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);

    final boolean previousUpdate;
    if (daemonCodeAnalyzer != null) {
      previousUpdate = ((DaemonCodeAnalyzerImpl)daemonCodeAnalyzer).isUpdateByTimerEnabled();
      daemonCodeAnalyzer.setUpdateByTimerEnabled(false);
    }
    else {
      previousUpdate = false;
    }

    final MyTemplateListener templateListener = new MyTemplateListener() {
      @Override
      protected void restoreDaemonUpdateState() {
        if (daemonCodeAnalyzer != null) {
          daemonCodeAnalyzer.setUpdateByTimerEnabled(previousUpdate);
        }
      }
    };

    final int offset = myEditor.getCaretModel().getOffset();

    Template template = builder.buildInlineTemplate();
    template.setToShortenLongNames(false);
    TextRange range = myScope.getTextRange();
    assert range != null;
    myHighlighters = new ArrayList<RangeHighlighter>();
    Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
    topLevelEditor.getCaretModel().moveToOffset(range.getStartOffset());

    TemplateManager.getInstance(myProject).startTemplate(topLevelEditor, template, templateListener);
    restoreOldCaretPositionAndSelection(offset);
    highlightTemplateVariables(template, topLevelEditor);
  }

  private void highlightTemplateVariables(Template template, Editor topLevelEditor) {
    //add highlights
    if (myHighlighters != null) { // can be null if finish is called during testing
      Map<TextRange, TextAttributes> rangesToHighlight = new HashMap<TextRange, TextAttributes>();
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
    Runnable runnable = new Runnable() {
      public void run() {
        myEditor.getCaretModel().moveToOffset(restoreCaretOffset(offset));
        restoreSelection();
      }
    };

    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
    if (lookup != null && lookup.getLookupStart() <= (restoreCaretOffset(offset))) {
      lookup.setFocused(false);
      lookup.performGuardedChange(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected void restoreSelection() {
  }

  protected int restoreCaretOffset(int offset) {
    return myCaretRangeMarker.isValid() ? myCaretRangeMarker.getStartOffset() : offset;
  }

  protected void navigateToAlreadyStarted(Document oldDocument, int exitCode) {
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(oldDocument);
    if (file != null) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final FileEditor[] editors = FileEditorManager.getInstance(myProject).getEditors(virtualFile);
        for (FileEditor editor : editors) {
          if (editor instanceof TextEditor) {
            final Editor textEditor = ((TextEditor)editor).getEditor();
            final TemplateState templateState = TemplateManagerImpl.getTemplateState(textEditor);
            if (templateState != null) {
              if (exitCode == DialogWrapper.OK_EXIT_CODE) {
                final TextRange range = templateState.getVariableRange(PRIMARY_VARIABLE_NAME);
                if (range != null) {
                  new OpenFileDescriptor(myProject, virtualFile, range.getStartOffset()).navigate(true);
                  return;
                }
              }
              else {
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
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      @Override
      public void run() {
        try {
          markAction[0] = StartMarkAction.start(myEditor, myProject, getCommandName());
        }
        catch (StartMarkAction.AlreadyStartedException e) {
          ex[0] = e;
        }
      }
    }, getCommandName(), null);
    if (ex[0] != null) throw ex[0];
    return markAction[0];
  }

  @Nullable
  protected PsiNamedElement getVariable() {
    if (myElementToRename != null && myElementToRename.isValid()) return myElementToRename;
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    if (psiFile != null) {
      return PsiTreeUtil.getParentOfType(psiFile.findElementAt(myRenameOffset.getStartOffset()), PsiNameIdentifierOwner.class);
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
    if (reference != null && !refs.contains(reference)) {
      refs.add(reference);
    }
  }

  protected void showDialogAdvertisement(final String actionId) {
    final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    final Shortcut[] shortcuts = keymap.getShortcuts(actionId);
    if (shortcuts.length > 0) {
      setAdvertisementText("Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to show dialog");
    }
  }

  public String getInitialName() {
    if (myInitialName == null) {
      final PsiNamedElement variable = getVariable();
      if (variable != null) {
        return variable.getName();
      }
    }
    return myInitialName;
  }

  protected void revertState() {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        final Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final TemplateState state = TemplateManagerImpl.getTemplateState(topLevelEditor);
            assert state != null;
            final int segmentsCount = state.getSegmentsCount();
            final Document document = topLevelEditor.getDocument();
            for (int i = 0; i < segmentsCount; i++) {
              final TextRange segmentRange = state.getSegmentRange(i);
              document.replaceString(segmentRange.getStartOffset(), segmentRange.getEndOffset(), myOldName);
            }
          }
        });
        if (!myProject.isDisposed() && myProject.isOpen()) {
          PsiDocumentManager.getInstance(myProject).commitDocument(topLevelEditor.getDocument());
        }
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

  private void addVariable(final PsiReference reference,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder,
                           int offset) {
    if (reference.getElement() == selectedElement &&
        contains(reference.getRangeInElement().shiftRight(selectedElement.getTextRange().getStartOffset()), offset)) {
      Expression expression = new MyExpression(getInitialName(), myNameSuggestions);
      builder.replaceElement(reference, PRIMARY_VARIABLE_NAME, expression, true);
    }
    else {
      builder.replaceElement(reference, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
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
      Expression expression = new MyExpression(getInitialName(), myNameSuggestions);
      builder.replaceElement(element, PRIMARY_VARIABLE_NAME, expression, true);
    }
    else if (textRange != null) {
      builder.replaceElement(element, textRange, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
    else {
      builder.replaceElement(element, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }


  public void setElementToRename(PsiNamedElement elementToRename) {
    myElementToRename = elementToRename;
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

  private static PsiElement getSelectedInEditorElement(@Nullable PsiElement nameIdentifier,
                                                       final Collection<PsiReference> refs,
                                                       Collection<Pair<PsiElement, TextRange>> stringUsages,
                                                       final int offset) {
    if (nameIdentifier != null) {
      final TextRange range = nameIdentifier.getTextRange();
      if (contains(range, offset)) return nameIdentifier;
    }

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (contains(ref.getRangeInElement().shiftRight(element.getTextRange().getStartOffset()), offset)) return element;
    }

    for (Pair<PsiElement, TextRange> stringUsage : stringUsages) {
      final PsiElement element = stringUsage.first;
      if (contains(stringUsage.second.shiftRight(element.getTextRange().getStartOffset()), offset)) return element;
    }

    LOG.assertTrue(false);
    return null;
  }

  private static boolean contains(final TextRange range, final int offset) {
    return range.getStartOffset() <= offset && offset <= range.getEndOffset();
  }

  protected class MyExpression extends Expression {
    private final String myName;
    private final LookupElement[] myLookupItems;

    protected MyExpression(String name, LinkedHashSet<String> names) {
      myName = name;
      if (names == null) {
        names = new LinkedHashSet<String>();
        for (NameSuggestionProvider provider : Extensions.getExtensions(NameSuggestionProvider.EP_NAME)) {
          provider.getSuggestedNames(myElementToRename, myElementToRename, names);
        }
      }
      myLookupItems = new LookupElement[names.size()];
      final Iterator<String> iterator = names.iterator();
      for (int i = 0; i < myLookupItems.length; i++) {
        final String suggestion = iterator.next();
        myLookupItems[i] = LookupElementBuilder.create(suggestion).setInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            if (shouldSelectAll()) return;
            final Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
            final TemplateState templateState = TemplateManagerImpl.getTemplateState(topLevelEditor);
            if (templateState != null) {
              final TextRange range = templateState.getCurrentVariableRange();
              if (range != null) {
                topLevelEditor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), suggestion);
              }
            }
          }
        });
      }
    }

    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return createLookupItems(myLookupItems, myName);
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return calculateResult(context);
    }

    public Result calculateResult(ExpressionContext context) {
      TemplateState templateState = TemplateManagerImpl.getTemplateState(myEditor);
      final TextResult insertedValue = templateState != null ? templateState.getVariableValue(PRIMARY_VARIABLE_NAME) : null;
      if (insertedValue != null) {
        if (!insertedValue.getText().isEmpty()) return insertedValue;
      }
      return new TextResult(myName);
    }

    @Override
    public String getAdvertisingText() {
      return myAdvertisementText;
    }
  }

  private abstract class MyTemplateListener extends TemplateEditingAdapter {

    protected abstract void restoreDaemonUpdateState();

    public void beforeTemplateFinished(final TemplateState templateState, Template template) {
      try {
        final TextResult value = templateState.getVariableValue(PRIMARY_VARIABLE_NAME);
        myInsertedName = value != null ? value.toString() : null;

        final int currentOffset = myEditor.getCaretModel().getOffset();
        myBeforeRevert =
          myRenameOffset != null && myRenameOffset.getEndOffset() >= currentOffset && myRenameOffset.getStartOffset() <= currentOffset
          ? myEditor.getDocument().createRangeMarker(myRenameOffset.getStartOffset(), currentOffset)
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
        super.templateFinished(template, brokenOff);
        if (!brokenOff) {
          bind = performRefactoring();
        }
        moveOffsetAfter(!brokenOff);
      }
      finally {
        if (!bind) {
          FinishMarkAction.finish(myProject, myEditor, myMarkAction);
          if (myBeforeRevert != null) {
            myBeforeRevert.dispose();
          }
        }
      }
    }

    public void templateCancelled(Template template) {
      try {
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        documentManager.commitAllDocuments();
        finish(false);
        moveOffsetAfter(false);
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            documentManager.doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
          }
        });
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
