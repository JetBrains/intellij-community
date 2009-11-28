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
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.Stack;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

  private final PsiNameIdentifierOwner myElementToRename;
  @NonNls private static final String PRIMARY_VARIABLE_NAME = "PrimaryVariable";
  @NonNls private static final String OTHER_VARIABLE_NAME = "OtherVariable";
  private ArrayList<RangeHighlighter> myHighlighters;
  private final Editor myEditor;
  private final Project myProject;

  private static final Stack<VariableInplaceRenamer> ourRenamersStack = new Stack<VariableInplaceRenamer>();

  public VariableInplaceRenamer(PsiNameIdentifierOwner elementToRename, Editor editor) {
    myElementToRename = elementToRename;
    myEditor = /*(editor instanceof EditorWindow)? ((EditorWindow)editor).getDelegate() : */editor;
    myProject = myElementToRename.getProject();
  }

  public boolean performInplaceRename() {
    final Collection<PsiReference> refs = ReferencesSearch.search(myElementToRename).findAll();

    final PsiReference reference = myElementToRename.getContainingFile().findReferenceAt(myEditor.getCaretModel().getOffset());
    if (reference != null && !refs.contains(reference)) {
      refs.add(reference);
    }

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
    final SearchScope searchScope = myElementToRename.getUseScope();
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

    final PsiElement nameIdentifier = myElementToRename.getNameIdentifier();
    PsiElement selectedElement = getSelectedInEditorElement(nameIdentifier, refs, myEditor.getCaretModel().getOffset());
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myElementToRename)) return true;

    if (nameIdentifier != null) addVariable(nameIdentifier, selectedElement, builder);
    for (PsiReference ref : refs) {
      addVariable(ref, selectedElement, builder);
    }
    
    final PsiElement scope1 = scope;
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
              public void beforeTemplateFinished(final TemplateState templateState, Template template) {
                finish();

                if (snapshot != null) {
                  TextResult value = templateState.getVariableValue(PRIMARY_VARIABLE_NAME);
                  if (value != null) {
                    final String newName = value.toString();
                    if (LanguageNamesValidation.INSTANCE.forLanguage(scope1.getLanguage()).isIdentifier(newName, myProject)) {
                      ApplicationManager.getApplication().runWriteAction(new Runnable() {
                        public void run() {
                          snapshot.apply(newName);
                        }
                      });
                    }
                  }
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
    PsiElement nameId = myElementToRename.getNameIdentifier();
    LOG.assertTrue(nameId != null);
    rangesToHighlight.put(nameId.getTextRange().shiftRight(PsiUtilBase.findInjectedElementOffsetInRealDocument(nameId)), colorsManager.getGlobalScheme().getAttributes(EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES));
    
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

  private static PsiElement getSelectedInEditorElement(final PsiElement nameIdentifier, final Collection<PsiReference> refs, final int offset) {
    if (nameIdentifier != null) {
      final TextRange range = nameIdentifier.getTextRange()/*.shiftRight(PsiUtilBase.findInjectedElementOffsetInRealDocument(nameIdentifier))*/;
      if (contains(range, offset)) return nameIdentifier;
    }

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      final TextRange range = element.getTextRange()/*.shiftRight(PsiUtilBase.findInjectedElementOffsetInRealDocument(ref.getElement()))*/;
      if (contains(range, offset)) return element;
    }

    LOG.assertTrue(false);
    return null;
  }

  private static boolean contains(final TextRange range, final int offset) {
    return range.getStartOffset() <= offset && offset <= range.getEndOffset();
  }

  private void addVariable(final PsiReference reference, final PsiElement selectedElement, final TemplateBuilderImpl builder) {
    if (reference.getElement() == selectedElement) {
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
