/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 30-Jan-2008
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TargetElementUtilBase {
  public static final int REFERENCED_ELEMENT_ACCEPTED = 0x01;
  public static final int ELEMENT_NAME_ACCEPTED = 0x02;
  public static final int LOOKUP_ITEM_ACCEPTED = 0x08;

  public static TargetElementUtilBase getInstance() {
    return ServiceManager.getService(TargetElementUtilBase.class);
  }

  public int getAllAccepted() {
    return REFERENCED_ELEMENT_ACCEPTED | ELEMENT_NAME_ACCEPTED | LOOKUP_ITEM_ACCEPTED;
  }

  /**
   * Accepts THIS or SUPER but not NEW_AS_CONSTRUCTOR.
   */
  public int getDefinitionSearchFlags() {
    return getAllAccepted();
  }

  /**
   * Accepts NEW_AS_CONSTRUCTOR but not THIS or SUPER.
   */
  public int getReferenceSearchFlags() {
    return getAllAccepted();
  }

  @Nullable
  public static PsiReference findReference(Editor editor) {
    PsiReference result = findReference(editor, editor.getCaretModel().getOffset());
    if (result == null) {
      final Integer offset = editor.getUserData(EditorActionUtil.EXPECTED_CARET_OFFSET);
      if (offset != null) {
        result = findReference(editor, offset);
      }
    }
    return result;
  }

  @Nullable
  public static PsiReference findReference(Editor editor, int offset) {
    Project project = editor.getProject();
    if (project == null) return null;

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;
    if (ApplicationManager.getApplication().isDispatchThread()) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }

    offset = adjustOffset(file, document, offset);

    if (file instanceof PsiCompiledFile) {
      return ((PsiCompiledFile) file).getDecompiledPsiFile().findReferenceAt(offset);
    }

    return file.findReferenceAt(offset);
  }

  /**
   * @deprecated adjust offset with PsiElement should be used instead to provide correct checking for identifier part
   */
  public static int adjustOffset(Document document, final int offset) {
    return adjustOffset(null, document, offset);
  }

  public static int adjustOffset(@Nullable PsiFile file, Document document, final int offset) {
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!isIdentifierPart(file, text, offset)) {
      correctedOffset--;
    }
    if (correctedOffset < 0 || !isIdentifierPart(file, text, correctedOffset)) return offset;
    return correctedOffset;
  }

  private static boolean isIdentifierPart(@Nullable PsiFile file, CharSequence text, int offset) {
    if (file != null) {
      for (TargetElementEvaluator evaluator : getInstance().targetElementEvaluator.allForLanguage(file.getLanguage())) {
        if (evaluator instanceof TargetElementEvaluatorEx && ((TargetElementEvaluatorEx)evaluator).isIdentifierPart(file, text, offset)) {
          return true;
        }
      }
    }
    return Character.isJavaIdentifierPart(text.charAt(offset));
  }

  @Nullable
  public static PsiElement findTargetElement(Editor editor, int flags) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final PsiElement result = getInstance().findTargetElement(editor, flags, editor.getCaretModel().getOffset());
    if (result != null) {
      return result;
    }
    final Integer offset = editor.getUserData(EditorActionUtil.EXPECTED_CARET_OFFSET);
    if (offset != null) {
      return getInstance().findTargetElement(editor, flags, offset);
    }
    return result;
  }

  public static boolean inVirtualSpace(@NotNull Editor editor, int offset) {
    if (offset == editor.getCaretModel().getOffset()) {
      return EditorUtil.inVirtualSpace(editor, editor.getCaretModel().getLogicalPosition());
    }

    return false;
  }

  @Nullable
  public PsiElement findTargetElement(Editor editor, int flags, int offset) {
    Project project = editor.getProject();
    if (project == null) return null;

    if ((flags & LOOKUP_ITEM_ACCEPTED) != 0) {
      PsiElement element = getTargetElementFromLookup(project);
      if (element != null) {
        return element;
      }
    }

    Document document = editor.getDocument();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    offset = adjustOffset(file, document, offset);

    if (file instanceof PsiCompiledFile) {
      file = ((PsiCompiledFile) file).getDecompiledPsiFile();
    }
    PsiElement element = file.findElementAt(offset);
    if ((flags & REFERENCED_ELEMENT_ACCEPTED) != 0) {
      final PsiElement referenceOrReferencedElement = getReferenceOrReferencedElement(file, editor, flags, offset);
      //if (referenceOrReferencedElement == null) {
      //  return getReferenceOrReferencedElement(file, editor, flags, offset);
      //}
      if (isAcceptableReferencedElement(element, referenceOrReferencedElement)) {
        return referenceOrReferencedElement;
      }
    }

    if (element == null) return null;

    if ((flags & ELEMENT_NAME_ACCEPTED) != 0) {
      if (element instanceof PsiNamedElement) return element;
      return getNamedElement(element, offset - element.getTextRange().getStartOffset());
    }
    return null;
  }

  @Nullable
  private static PsiElement getTargetElementFromLookup(Project project) {
    Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null) {
      LookupElement item = activeLookup.getCurrentItem();
      final PsiElement psi = item == null ? null : CompletionUtil.getTargetElement(item);
      if (psi != null && psi.isValid()) {
        return psi;
      }
    }
    return null;
  }

  protected boolean isAcceptableReferencedElement(final PsiElement element, final PsiElement referenceOrReferencedElement) {
    return referenceOrReferencedElement != null &&
        referenceOrReferencedElement.isValid();
  }

  @Nullable
  public PsiElement adjustElement(final Editor editor, final int flags, final PsiElement element, final PsiElement contextElement) {
    return element;
  }

  @Nullable
  public PsiElement adjustReference(@NotNull PsiReference ref){
    return null;
  }

  @Nullable
  public PsiElement getNamedElement(@Nullable final PsiElement element, final int offsetInElement) {
    if (element == null) return null;

    final List<PomTarget> targets = ContainerUtil.newArrayList();
    final Consumer<PomTarget> consumer = new Consumer<PomTarget>() {
      @Override
      public void consume(PomTarget target) {
        if (target instanceof PsiDeclaredTarget) {
          final PsiDeclaredTarget declaredTarget = (PsiDeclaredTarget)target;
          final PsiElement navigationElement = declaredTarget.getNavigationElement();
          final TextRange range = declaredTarget.getNameIdentifierRange();
          if (range != null && !range.shiftRight(navigationElement.getTextRange().getStartOffset())
            .contains(element.getTextRange().getStartOffset() + offsetInElement)) {
            return;
          }
        }
        targets.add(target);
      }
    };

    PsiElement parent = element;

    int offset = offsetInElement;
    while (parent != null) {
      for (PomDeclarationSearcher searcher : PomDeclarationSearcher.EP_NAME.getExtensions()) {
        searcher.findDeclarationsAt(parent, offset, consumer);
        if (!targets.isEmpty()) {
          final PomTarget target = targets.get(0);
          return target == null ? null : PomService.convertToPsi(element.getProject(), target);
        }
      }
      offset += parent.getStartOffsetInParent();
      parent = parent.getParent();
    }

    return getNamedElement(element);
  }


  @Nullable
  protected PsiElement getNamedElement(@Nullable final PsiElement element) {
    PsiElement parent;
    if ((parent = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false)) != null) {
      // A bit hacky depends on navigation offset correctly overridden
      assert element != null : "notnull parent?";
      if (parent.getTextOffset() == element.getTextRange().getStartOffset()) {
        return parent;
      }
    }
    return null;
  }

  @Nullable
  protected PsiElement getReferenceOrReferencedElement(PsiFile file, Editor editor, int flags, int offset) {
    PsiReference ref = findReference(editor, offset);
    if (ref == null) return null;

    final Language language = ref.getElement().getLanguage();
    final List<TargetElementEvaluator> evaluators = targetElementEvaluator.allForLanguage(language);
    for (TargetElementEvaluator evaluator : evaluators) {
      final PsiElement element = evaluator.getElementByReference(ref, flags);
      if (element != null) return element;
    }

    PsiManager manager = file.getManager();
    PsiElement refElement = ref.resolve();
    if (refElement == null) {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        DaemonCodeAnalyzer.getInstance(manager.getProject()).updateVisibleHighlighters(editor);
      }
      return null;
    }
    else {
      return refElement;
    }
  }

  public Collection<PsiElement> getTargetCandidates(PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      final ArrayList<PsiElement> navigatableResults = new ArrayList<PsiElement>(results.length);

      for(ResolveResult r:results) {
        PsiElement element = r.getElement();
        if (EditSourceUtil.canNavigate(element) || element instanceof Navigatable && ((Navigatable)element).canNavigateToSource()) {
          navigatableResults.add(element);
        }
      }

      return navigatableResults;
    }
    PsiElement resolved = reference.resolve();
    if (resolved instanceof NavigationItem) {
      return Collections.singleton(resolved);
    }
    return Collections.emptyList();
  }

  public PsiElement getGotoDeclarationTarget(final PsiElement element, final PsiElement navElement) {
    return navElement;
  }

  public boolean includeSelfInGotoImplementation(final PsiElement element) {
    final TargetElementEvaluator elementEvaluator = element != null ? targetElementEvaluator.forLanguage(element.getLanguage()):null;
    return elementEvaluator == null || elementEvaluator.includeSelfInGotoImplementation(element);
  }

  protected final LanguageExtension<TargetElementEvaluator> targetElementEvaluator = new LanguageExtension<TargetElementEvaluator>("com.intellij.targetElementEvaluator");

  public boolean acceptImplementationForReference(PsiReference reference, PsiElement element) {
    return true;
  }
  
  public SearchScope getSearchScope(Editor editor, PsiElement element) {
    return PsiSearchHelper.SERVICE.getInstance(element.getProject()).getUseScope(element);
  }
}
