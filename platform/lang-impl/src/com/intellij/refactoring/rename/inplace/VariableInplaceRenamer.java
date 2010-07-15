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
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.AutomaticRenamingDialog;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Stack;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author ven
 */
public class VariableInplaceRenamer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.inplace.VariableInplaceRenamer");
  public static final LanguageExtension<ResolveSnapshotProvider> INSTANCE = new LanguageExtension<ResolveSnapshotProvider>(
    "com.intellij.rename.inplace.resolveSnapshotProvider"
  );

  private final PsiNamedElement myElementToRename;
  @NonNls private static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OtherVariable";
  private ArrayList<RangeHighlighter> myHighlighters;
  private final Editor myEditor;
  private final Project myProject;

  private static final Stack<VariableInplaceRenamer> ourRenamersStack = new Stack<VariableInplaceRenamer>();

  public VariableInplaceRenamer(@NotNull PsiNamedElement elementToRename, Editor editor) {
    myElementToRename = elementToRename;
    myEditor = /*(editor instanceof EditorWindow)? ((EditorWindow)editor).getDelegate() : */editor;
    myProject = myElementToRename.getProject();
  }

  public boolean performInplaceRename() {
    if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(myElementToRename)) {
      return false;
    }
    
    final Collection<PsiReference> refs = ReferencesSearch.search(myElementToRename).findAll();

    addReferenceAtCaret(refs);

    final FileViewProvider fileViewProvider = myElementToRename.getContainingFile().getViewProvider();
    VirtualFile file = getTopLevelVirtualFile(fileViewProvider);

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

    final Map<TextRange, TextAttributes> rangesToHighlight = new THashMap<TextRange, TextAttributes>();
    //it is crucial to highlight AFTER the template is started, so we collect ranges first
    collectElementsToHighlight(rangesToHighlight, refs);

    final HighlightManager highlightManager = HighlightManager.getInstance(myProject);

    PsiElement scope = null;
    final SearchScope searchScope = myElementToRename.getManager().getSearchHelper().getUseScope(myElementToRename);
    if (searchScope instanceof LocalSearchScope) {
      final PsiElement[] elements = ((LocalSearchScope)searchScope).getScope();
      scope = PsiTreeUtil.findCommonParent(elements);
    }

    if (scope == null) {
      return false; // Should have valid local search scope for inplace rename
    }

    final PsiFile containingFile = scope.getContainingFile();
    if (containingFile == null){
      return false; // Should have valid local search scope for inplace rename
    }
    final PsiElement context = containingFile.getContext();
    if (context != null) {
      scope = context.getContainingFile();
    }

    String stringToSearch = myElementToRename.getName();
    if (stringToSearch != null &&
        !TextOccurrencesUtil.processUsagesInStringsAndComments(myElementToRename, stringToSearch, true, new PairProcessor<PsiElement, TextRange>() {
            public boolean process(PsiElement psiElement, TextRange textRange) {
              return false;
            }
          })) {
      return false;
    }

    ResolveSnapshotProvider resolveSnapshotProvider = INSTANCE.forLanguage(scope.getLanguage());
    final ResolveSnapshotProvider.ResolveSnapshot snapshot = resolveSnapshotProvider != null ?
      resolveSnapshotProvider.createSnapshot(scope):null;
    final TemplateBuilderImpl builder = new TemplateBuilderImpl(scope);

    PsiElement nameIdentifier = myElementToRename instanceof PsiNameIdentifierOwner ? ((PsiNameIdentifierOwner)myElementToRename).getNameIdentifier() : null;
    int offset = myEditor.getCaretModel().getOffset();
    PsiElement selectedElement = getSelectedInEditorElement(nameIdentifier, refs, offset);
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myElementToRename)) return true;

    if (nameIdentifier != null) addVariable(nameIdentifier, selectedElement, builder);
    for (PsiReference ref : refs) {
      addVariable(ref, selectedElement, builder, offset);
    }
    
    final PsiElement scope1 = scope;
    final int renameOffset = myElementToRename.getTextOffset();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            int offset = myEditor.getCaretModel().getOffset();
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

                if (snapshot != null) {
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
                if (myNewName != null) {
                  performAutomaticRename(myNewName, PsiTreeUtil.getParentOfType(containingFile.findElementAt(renameOffset), PsiNameIdentifierOwner.class));
                }
              }

              public void templateCancelled(Template template) {
                finish();
              }
            });

            //move to old offset
            final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myEditor);
            final boolean lookupShown = lookup != null && lookup.getLookupStart() < offset;
            if (lookupShown) {
              lookup.setAdditionalPrefix(myEditor.getDocument().getCharsSequence().subSequence(lookup.getLookupStart(), offset).toString());
            }
            myEditor.getCaretModel().moveToOffset(offset);
            if (lookupShown) {
              lookup.setAdditionalPrefix("");
            }

            //add highlights
            addHighlights(rangesToHighlight, topLevelEditor, myHighlighters, highlightManager);
          }
        });
      }
    }, RefactoringBundle.message("rename.title"), null);

    return true;
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
      final HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      for (RangeHighlighter highlighter : myHighlighters) {
        highlightManager.removeSegmentHighlighter(myEditor, highlighter);
      }

      myHighlighters = null;
    }
  }

  private void collectElementsToHighlight(Map<TextRange, TextAttributes> rangesToHighlight, Collection<PsiReference> refs) {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    if (myElementToRename instanceof PsiNameIdentifierOwner) {
      PsiElement nameId = ((PsiNameIdentifierOwner)myElementToRename).getNameIdentifier();
      LOG.assertTrue(nameId != null);
      rangesToHighlight.put(nameId.getTextRange().shiftRight(PsiUtilBase.findInjectedElementOffsetInRealDocument(nameId)), colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES));
    }

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      TextRange range = ref.getRangeInElement().shiftRight(
        element.getTextRange().getStartOffset() +
          PsiUtilBase.findInjectedElementOffsetInRealDocument(element)
      );

      ReadWriteAccessDetector writeAccessDetector = ReadWriteAccessDetector.findDetector(element);
      // TODO: read / write usages
      boolean isForWrite = writeAccessDetector != null &&
        ReadWriteAccessDetector.Access.Write == writeAccessDetector.getExpressionAccess(element);
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(isForWrite ?
                                                                                EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES :
                                                                                EditorColors.SEARCH_RESULT_ATTRIBUTES);
      rangesToHighlight.put(range, attributes);
    }
  }

  private static void addHighlights(@NotNull Map<TextRange, TextAttributes> ranges, @NotNull Editor editor, @NotNull Collection<RangeHighlighter> highlighters, @NotNull HighlightManager highlightManager) {
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

  private void addVariable(final PsiReference reference, final PsiElement selectedElement, final TemplateBuilderImpl builder, int offset) {
    if (reference.getElement() == selectedElement &&
        contains(reference.getRangeInElement().shiftRight(selectedElement.getTextRange().getStartOffset()), offset)) {
      Expression expression = new MyExpression(myElementToRename.getName());
      builder.replaceElement(reference, PRIMARY_VARIABLE_NAME, expression, true);
    }
    else {
      builder.replaceElement(reference, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  private void addVariable(final PsiElement element, final PsiElement selectedElement, final TemplateBuilderImpl builder) {
    if (element == selectedElement) {
      Expression expression = new MyExpression(myElementToRename.getName());
      builder.replaceElement(element, PRIMARY_VARIABLE_NAME, expression, true);
    }
    else {
      builder.replaceElement(element, OTHER_VARIABLE_NAME, PRIMARY_VARIABLE_NAME, false);
    }
  }

  private class MyExpression extends Expression {
    private final String myName;
    private final LookupElement[] myLookupItems;

    private MyExpression(String name) {
      myName = name;
      List<String> names = new ArrayList<String>();
      for(NameSuggestionProvider provider: Extensions.getExtensions(NameSuggestionProvider.EP_NAME)) {
        provider.getSuggestedNames(myElementToRename, myElementToRename, names);
      }
      myLookupItems = new LookupElement[names.size()];
      for (int i = 0; i < myLookupItems.length; i++) {
        myLookupItems[i] = LookupElementBuilder.create(names.get(i));
      }
    }

    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return myLookupItems;
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return new TextResult(myName);
    }

    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myName);
    }
  }
}
