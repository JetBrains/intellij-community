/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.AutomaticRenamingDialog;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.ui.components.JBList;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Stack;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.*;

/**
 * @author ven
 */
public class VariableInplaceRenamer {
  public static final Key<VariableInplaceRenamer> INPLACE_RENAMER = Key.create("EditorInplaceRenamer");
  protected static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.VariableInplaceRenamer");
  public static final LanguageExtension<ResolveSnapshotProvider> INSTANCE = new LanguageExtension<ResolveSnapshotProvider>(
    "com.intellij.rename.inplace.resolveSnapshotProvider"
  );

  private final PsiNamedElement myElementToRename;
  @NonNls protected static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OtherVariable";
  private ArrayList<RangeHighlighter> myHighlighters;
  private final Editor myEditor;
  private final Project myProject;
  private RangeMarker myRenameOffset;

  public void setAdvertisementText(String advertisementText) {
    myAdvertisementText = advertisementText;
  }

  private String myAdvertisementText;

  private static final Stack<VariableInplaceRenamer> ourRenamersStack = new Stack<VariableInplaceRenamer>();

  public VariableInplaceRenamer(@NotNull PsiNamedElement elementToRename, Editor editor) {
    myElementToRename = elementToRename;
    myEditor = /*(editor instanceof EditorWindow)? ((EditorWindow)editor).getDelegate() : */editor;
    myProject = myElementToRename.getProject();
    myRenameOffset = myEditor.getDocument().createRangeMarker(myElementToRename.getTextRange());
  }

  public boolean performInplaceRename() {
    return performInplaceRename(true, null);
  }

  public boolean performInplaceRename(boolean processTextOccurrences, LinkedHashSet<String> nameSuggestions) {
    if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(myElementToRename)) {
      return false;
    }

    final FileViewProvider fileViewProvider = myElementToRename.getContainingFile().getViewProvider();
    VirtualFile file = getTopLevelVirtualFile(fileViewProvider);

    SearchScope referencesSearchScope = file == null || ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file)
      ? ProjectScope.getProjectScope(myElementToRename.getProject())
      : new LocalSearchScope(myElementToRename.getContainingFile());

    final Collection<PsiReference> refs = ReferencesSearch.search(myElementToRename, referencesSearchScope, false).findAll();

    addReferenceAtCaret(refs);

    for (PsiReference ref : refs) {
      final FileViewProvider usageViewProvider = ref.getElement().getContainingFile().getViewProvider();

      if (getTopLevelVirtualFile(usageViewProvider) != file) {
        return false;
      }
    }

    while (!ourRenamersStack.isEmpty()) {
      ourRenamersStack.peek().finish();
    }

    ourRenamersStack.push(this);

    PsiElement scope = checkLocalScope();

    if (scope == null) {
      return false; // Should have valid local search scope for inplace rename
    }

    final PsiFile containingFile = scope.getContainingFile();
    if (containingFile == null){
      return false; // Should have valid local search scope for inplace rename
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myElementToRename)) return false;

    final List<Pair<PsiElement, TextRange>> stringUsages = new ArrayList<Pair<PsiElement, TextRange>>();
    collectAdditionalElementsToRename(processTextOccurrences, stringUsages);
    if (appendAdditionalElement(stringUsages)) {
      runRenameTemplate(nameSuggestions, refs, stringUsages, scope, containingFile);
    } else {
      new RenameChooser(myEditor).showChooser(refs, stringUsages, nameSuggestions, scope, containingFile);
    }

    myEditor.putUserData(INPLACE_RENAMER, this);
    return true;
  }

  @Nullable
  protected PsiElement checkLocalScope() {
    final SearchScope searchScope = myElementToRename.getManager().getSearchHelper().getUseScope(myElementToRename);
    if (searchScope instanceof LocalSearchScope) {
      final PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      return PsiTreeUtil.findCommonParent(elements);
    }

    return null;
  }

  protected boolean appendAdditionalElement(List<Pair<PsiElement, TextRange>> stringUsages) {
    return stringUsages.isEmpty();
  }

  protected void collectAdditionalElementsToRename(boolean processTextOccurrences, final List<Pair<PsiElement, TextRange>> stringUsages) {
    final String stringToSearch = myElementToRename.getName();
    if (processTextOccurrences && stringToSearch != null) {
      TextOccurrencesUtil
        .processUsagesInStringsAndComments(myElementToRename, stringToSearch, true, new PairProcessor<PsiElement, TextRange>() {
          public boolean process(PsiElement psiElement, TextRange textRange) {
            stringUsages.add(Pair.create(psiElement, textRange));
            return true;
          }
        });
    }
  }

  private void runRenameTemplate(LinkedHashSet<String> nameSuggestions,
                                 Collection<PsiReference> refs,
                                 Collection<Pair<PsiElement, TextRange>> stringUsages,
                                 PsiElement scope,
                                 final PsiFile containingFile) {
    final PsiElement context = containingFile.getContext();
    if (context != null) {
      scope = context.getContainingFile();
    }

    final Map<TextRange, TextAttributes> rangesToHighlight = new THashMap<TextRange, TextAttributes>();
    //it is crucial to highlight AFTER the template is started, so we collect ranges first
    collectElementsToHighlight(rangesToHighlight, refs, stringUsages);

    final HighlightManager highlightManager = HighlightManager.getInstance(myProject);
    ResolveSnapshotProvider resolveSnapshotProvider = INSTANCE.forLanguage(scope.getLanguage());
    final ResolveSnapshotProvider.ResolveSnapshot snapshot = resolveSnapshotProvider != null ?
      resolveSnapshotProvider.createSnapshot(scope):null;
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(scope);

    PsiElement nameIdentifier = myElementToRename instanceof PsiNameIdentifierOwner ? ((PsiNameIdentifierOwner)myElementToRename).getNameIdentifier() : null;
    int offset = myEditor.getCaretModel().getOffset();
    PsiElement selectedElement = getSelectedInEditorElement(nameIdentifier, refs, offset);

    if (nameIdentifier != null) addVariable(nameIdentifier, selectedElement, builder, nameSuggestions);
    for (PsiReference ref : refs) {
      addVariable(ref, selectedElement, builder, offset, nameSuggestions);
    }
    for (Pair<PsiElement, TextRange> usage : stringUsages) {
      addVariable(usage.first, usage.second, builder);
    }
    addAdditionalVariables(builder);

    final PsiElement scope1 = scope;
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final int offset = myEditor.getCaretModel().getOffset();
            final SelectionModel selectionModel = myEditor.getSelectionModel();
            final TextRange selectedRange = preserveSelectedRange(selectionModel);
            Template template = builder.buildInlineTemplate();
            template.setToShortenLongNames(false);
            TextRange range = scope1.getTextRange();
            assert range != null;
            myHighlighters = new ArrayList<RangeHighlighter>();
            Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(myEditor);
            topLevelEditor.getCaretModel().moveToOffset(range.getStartOffset());
            TemplateManager.getInstance(myProject).startTemplate(topLevelEditor, template, new TemplateEditingAdapter() {
              private String myNewName = null;
              public void beforeTemplateFinished(final TemplateState templateState, Template template) {
                finish();

                if (snapshot != null && performAutomaticRename()) {
                  TextResult value = templateState.getVariableValue(PRIMARY_VARIABLE_NAME);
                  if (value != null) {
                    myNewName = value.toString();
                    if (LanguageNamesValidation.INSTANCE.forLanguage(scope1.getLanguage()).isIdentifier(myNewName, myProject)) {
                      ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        public void run() {
                          snapshot.apply(myNewName);
                        }
                      });
                    }
                  }
                }
              }

              @Override
              public void templateFinished(Template template, boolean brokenOff) {
                super.templateFinished(template, brokenOff);
                moveOffsetAfter(!brokenOff);
                if (myNewName != null) {
                  performAutomaticRename(myNewName, getVariable());
                }
              }

              public void templateCancelled(Template template) {
                finish();
                moveOffsetAfter(false);
              }
            });

            //move to old offset
            Runnable runnable = new Runnable() {
              public void run() {
                myEditor.getCaretModel().moveToOffset(offset);
                if (selectedRange != null){
                  myEditor.getSelectionModel().setSelection(selectedRange.getStartOffset(), selectedRange.getEndOffset());
                }
              }
            };

            final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
            if (lookup != null && lookup.getLookupStart() <= offset) {
              lookup.setFocused(false);
              lookup.performGuardedChange(runnable);
            } else {
              runnable.run();
            }

            //add highlights
            if (myHighlighters != null) { // can be null if finish is called during testing
              addHighlights(rangesToHighlight, topLevelEditor, myHighlighters, highlightManager);
            }
          }
        });
      }
    }, RefactoringBundle.message("rename.title"), null);

  }

  @Nullable
  protected TextRange preserveSelectedRange(SelectionModel selectionModel) {
    if (selectionModel.hasSelection()) {
      return new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    }
    return null;
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

  protected boolean performAutomaticRename() {
    return true;
  }

  protected void moveOffsetAfter(boolean success) {
  }

  protected void addAdditionalVariables(TemplateBuilderImpl builder) {
  }

  protected void addReferenceAtCaret(Collection<PsiReference> refs) {
    PsiFile myEditorFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    // Note, that myEditorFile can be different from myElement.getContainingFile() e.g. in injections: myElement declaration in one
    // file / usage in another !
    final PsiReference reference = (myEditorFile != null ?
      myEditorFile:myElementToRename.getContainingFile()).findReferenceAt(myEditor.getCaretModel().getOffset());
    if (reference != null && !refs.contains(reference)) {
      refs.add(reference);
    }
  }

  public void performAutomaticRename(final String newName, final PsiElement elementToRename) {
    for (AutomaticRenamerFactory renamerFactory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
      if (renamerFactory.isApplicable(elementToRename)) {
        final List<UsageInfo> usages = new ArrayList<UsageInfo>();
        final AutomaticRenamer renamer =
          renamerFactory.createRenamer(elementToRename, newName, new ArrayList<UsageInfo>());
        if (renamer.hasAnythingToRename()) {
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            final AutomaticRenamingDialog renamingDialog = new AutomaticRenamingDialog(myProject, renamer);
            renamingDialog.show();
            if (!renamingDialog.isOK()) return;
          }

          final Runnable runnable = new Runnable() {
            public void run() {
              renamer.findUsages(usages, false, false);
            }
          };

          if (!ProgressManager.getInstance()
            .runProcessWithProgressSynchronously(runnable, RefactoringBundle.message("searching.for.variables"), true, myProject)) {
            return;
          }

          final UsageInfo[] usageInfos = usages.toArray(new UsageInfo[usages.size()]);
          final MultiMap<PsiElement,UsageInfo> classified = RenameProcessor.classifyUsages(renamer.getElements(), usageInfos);
          for (final PsiNamedElement element : renamer.getElements()) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                final String newElementName = renamer.getNewName(element);
                if (newElementName != null) {
                  final Collection<UsageInfo> infos = classified.get(element);
                  RenameUtil.doRenameGenericNamedElement(element, newElementName, infos.toArray(new UsageInfo[infos.size()]), null);
                }
              }
            });
          }
        }
      }
    }
  }

  private static VirtualFile getTopLevelVirtualFile(final FileViewProvider fileViewProvider) {
    VirtualFile file = fileViewProvider.getVirtualFile();
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    return file;
  }

  @TestOnly
  public static void checkCleared(){
    try {
      assert ourRenamersStack.isEmpty() : ourRenamersStack;
    }
    finally {
      ourRenamersStack.clear();
    }
  }

  public void finish() {
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

  private void collectElementsToHighlight(Map<TextRange, TextAttributes> rangesToHighlight,
                                          Collection<PsiReference> refs,
                                          Collection<Pair<PsiElement, TextRange>> stringUsages) {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    if (myElementToRename instanceof PsiNameIdentifierOwner) {
      PsiElement nameId = ((PsiNameIdentifierOwner)myElementToRename).getNameIdentifier();
      LOG.assertTrue(nameId != null);
      TextRange range = InjectedLanguageManager.getInstance(myProject).injectedToHost(nameId, nameId.getTextRange());
      rangesToHighlight.put(range, colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES));
    }

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      TextRange range = ref.getRangeInElement().shiftRight(
        InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, element.getTextRange().getStartOffset()));

      ReadWriteAccessDetector writeAccessDetector = ReadWriteAccessDetector.findDetector(element);
      // TODO: read / write usages
      boolean isForWrite = writeAccessDetector != null &&
        ReadWriteAccessDetector.Access.Write == writeAccessDetector.getExpressionAccess(element);
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(isForWrite ?
                                                                                EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES :
                                                                                EditorColors.SEARCH_RESULT_ATTRIBUTES);
      rangesToHighlight.put(range, attributes);
    }

    collectAdditionalRangesToHighlight(rangesToHighlight, stringUsages, colorsManager);
  }

  protected void collectAdditionalRangesToHighlight(Map<TextRange, TextAttributes> rangesToHighlight,
                                                    Collection<Pair<PsiElement, TextRange>> stringUsages,
                                                    EditorColorsManager colorsManager) {
    for (Pair<PsiElement, TextRange> usage : stringUsages) {
      final TextRange range = usage.second.shiftRight(usage.first.getTextOffset());
      final TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      rangesToHighlight.put(range, attributes);
    }
  }

  protected void addHighlights(@NotNull Map<TextRange, TextAttributes> ranges, @NotNull Editor editor, @NotNull Collection<RangeHighlighter> highlighters, @NotNull HighlightManager highlightManager) {
    for (Map.Entry<TextRange,TextAttributes> entry : ranges.entrySet()) {
      TextRange range = entry.getKey();
      TextAttributes attributes = entry.getValue();
      highlightManager.addOccurrenceHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, 0, highlighters, null);
    }

    for (RangeHighlighter highlighter : highlighters) {
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
  }

  private static PsiElement getSelectedInEditorElement(@Nullable PsiElement nameIdentifier, final Collection<PsiReference> refs, final int offset) {
    if (nameIdentifier != null) {
      final TextRange range = nameIdentifier.getTextRange()/*.shiftRight(PsiUtilBase.findInjectedElementOffsetInRealDocument(nameIdentifier))*/;
      if (contains(range, offset)) return nameIdentifier;
    }

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (contains(ref.getRangeInElement().shiftRight(element.getTextRange().getStartOffset()), offset)) return element;
    }

    LOG.assertTrue(false);
    return null;
  }

  private static boolean contains(final TextRange range, final int offset) {
    return range.getStartOffset() <= offset && offset <= range.getEndOffset();
  }

  private void addVariable(final PsiReference reference,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder,
                           int offset,
                           final LinkedHashSet<String> names) {
    if (reference.getElement() == selectedElement &&
        contains(reference.getRangeInElement().shiftRight(selectedElement.getTextRange().getStartOffset()), offset)) {
      Expression expression = new MyExpression(myElementToRename.getName(), names);
      builder.replaceElement(reference, PRIMARY_VARIABLE_NAME, expression, true);
    }
    else {
      builder.replaceElement(reference, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  private void addVariable(final PsiElement element,
                           final PsiElement selectedElement,
                           final TemplateBuilderImpl builder,
                           final LinkedHashSet<String> names) {
    if (element == selectedElement) {
      Expression expression = new MyExpression(myElementToRename.getName(), names);
      builder.replaceElement(element, PRIMARY_VARIABLE_NAME, expression, true);
    }
    else {
      builder.replaceElement(element, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  private static void addVariable(PsiElement element,
                                  TextRange textRange,
                                  TemplateBuilderImpl builder) {
    builder.replaceElement(element, textRange, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
  }

  protected LookupElement[] createLookupItems(final LookupElement[] lookupItems, final String name) {
    return lookupItems;
  }

  private class MyExpression extends Expression {
    private final String myName;
    private final LookupElement[] myLookupItems;

    private MyExpression(String name, LinkedHashSet<String> names) {
      myName = name;
      if (names == null) {
        names = new LinkedHashSet<String>();
        for(NameSuggestionProvider provider: Extensions.getExtensions(NameSuggestionProvider.EP_NAME)) {
          provider.getSuggestedNames(myElementToRename, myElementToRename, names);
        }
      }
      myLookupItems = new LookupElement[names.size()];
      final Iterator<String> iterator = names.iterator();
      for (int i = 0; i < myLookupItems.length; i++) {
        myLookupItems[i] = LookupElementBuilder.create(iterator.next());
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

  private class RenameChooser {
    @NonNls private static final String CODE_OCCURRENCES = "Rename code occurrences";
    @NonNls private static final String ALL_OCCURRENCES = "Rename all occurrences";
    private final Set<RangeHighlighter> myRangeHighlighters = new HashSet<RangeHighlighter>();
    private final Editor myEditor;
    private final TextAttributes myAttributes;

    public RenameChooser(Editor editor) {
      myEditor = editor;
      myAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    }

    public void showChooser(final Collection<PsiReference> refs,
                            final List<Pair<PsiElement, TextRange>> stringUsages,
                            final LinkedHashSet<String> nameSuggestions,
                            final PsiElement scope,
                            final PsiFile containingFile) {

      final DefaultListModel model = new DefaultListModel();
      model.addElement(CODE_OCCURRENCES);
      model.addElement(ALL_OCCURRENCES);
      final JList list = new JBList(model);

      list.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(final ListSelectionEvent e) {
          final String selectedValue = (String)list.getSelectedValue();
          if (selectedValue == null) return;
          dropHighlighters();
          final MarkupModel markupModel = myEditor.getMarkupModel();

          if (selectedValue == ALL_OCCURRENCES) {
            for (Pair<PsiElement, TextRange> pair : stringUsages) {
              final TextRange textRange = pair.second.shiftRight(pair.first.getTextOffset());
              final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
                textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1, myAttributes,
                HighlighterTargetArea.EXACT_RANGE);
              myRangeHighlighters.add(rangeHighlighter);
            }
          }

          for (PsiReference reference : refs) {
            final PsiElement element = reference.getElement();
            if (element == null) continue;
            final TextRange textRange = element.getTextRange();
            final RangeHighlighter rangeHighlighter = markupModel.addRangeHighlighter(
              textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.SELECTION - 1, myAttributes,
              HighlighterTargetArea.EXACT_RANGE);
            myRangeHighlighters.add(rangeHighlighter);
          }
        }
      });

      JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("String occurrences found")
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(new Runnable() {
          public void run() {
            runRenameTemplate(nameSuggestions, refs, list.getSelectedValue() == ALL_OCCURRENCES ? stringUsages : new ArrayList<Pair<PsiElement, TextRange>>(), scope, containingFile);
          }
        })
        .addListener(new JBPopupAdapter() {
          @Override
          public void onClosed(LightweightWindowEvent event) {
            dropHighlighters();
          }
        })
        .createPopup().showInBestPositionFor(myEditor);
    }



    private void dropHighlighters() {
      for (RangeHighlighter highlight : myRangeHighlighters) {
        highlight.dispose();
      }
      myRangeHighlighters.clear();
    }
  }
}
