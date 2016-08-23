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
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.extensions.Extensions;
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
import com.intellij.util.BitUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public class TargetElementUtil extends TargetElementUtilBase {
  /**
   * A flag used in {@link #findTargetElement(Editor, int, int)} indicating that if a reference is found at the specified offset,
   * it should be resolved and the result returned.
   */
  public static final int REFERENCED_ELEMENT_ACCEPTED = 0x01;

  /**
   * A flag used in {@link #findTargetElement(Editor, int, int)} indicating that if a element declaration name (e.g. class name identifier)
   * is found at the specified offset, the declared element should be returned.
   */
  public static final int ELEMENT_NAME_ACCEPTED = 0x02;

  /**
   * A flag used in {@link #findTargetElement(Editor, int, int)} indicating that if a lookup (e.g. completion) is shown in the editor,
   * the PSI element corresponding to the selected lookup item should be returned.
   */
  public static final int LOOKUP_ITEM_ACCEPTED = 0x08;

  public static TargetElementUtil getInstance() {
    return ServiceManager.getService(TargetElementUtil.class);
  }

  @Override
  public int getAllAccepted() {
    int result = REFERENCED_ELEMENT_ACCEPTED | ELEMENT_NAME_ACCEPTED | LOOKUP_ITEM_ACCEPTED;
    for (TargetElementUtilExtender each : Extensions.getExtensions(TargetElementUtilExtender.EP_NAME)) {
      result |= each.getAllAdditionalFlags();
    }
    return result;
  }

  @Override
  public int getDefinitionSearchFlags() {
    int result = getAllAccepted();
    for (TargetElementUtilExtender each : Extensions.getExtensions(TargetElementUtilExtender.EP_NAME)) {
      result |= each.getAdditionalDefinitionSearchFlags();
    }
    return result;
  }

  @Override
  public int getReferenceSearchFlags() {
    int result = getAllAccepted();
    for (TargetElementUtilExtender each : Extensions.getExtensions(TargetElementUtilExtender.EP_NAME)) {
      result |= each.getAdditionalReferenceSearchFlags();
    }
    return result;
  }

  @Nullable
  public static PsiReference findReference(@NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    PsiReference result = findReference(editor, offset);
    if (result == null) {
      int expectedCaretOffset = editor instanceof EditorEx ? ((EditorEx)editor).getExpectedCaretOffset() : offset;
      if (expectedCaretOffset != offset) {
        result = findReference(editor, expectedCaretOffset);
      }
    }
    return result;
  }

  @Nullable
  public static PsiReference findReference(@NotNull Editor editor, int offset) {
    Project project = editor.getProject();
    if (project == null) return null;

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    offset = adjustOffset(file, document, offset);

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
      TargetElementEvaluatorEx evaluator = getInstance().getElementEvaluatorsEx(file.getLanguage());
      if (evaluator != null && evaluator.isIdentifierPart(file, text, offset)) return true;
    }
    return Character.isJavaIdentifierPart(text.charAt(offset));
  }

  public static boolean inVirtualSpace(@NotNull Editor editor, int offset) {
    return offset == editor.getCaretModel().getOffset()
           && EditorUtil.inVirtualSpace(editor, editor.getCaretModel().getLogicalPosition());
  }

  /**
   * Note: this method can perform slow PSI activity (e.g. {@link PsiReference#resolve()}, so please avoid calling it from Swing thread.
   * @param editor editor
   * @param flags a combination of {@link #REFERENCED_ELEMENT_ACCEPTED}, {@link #ELEMENT_NAME_ACCEPTED}, {@link #LOOKUP_ITEM_ACCEPTED}
   * @return a PSI element declared or referenced at the editor caret position, or selected in the {@link Lookup} if shown in the editor,
   * depending on the flags passed.
   * @see #findTargetElement(Editor, int, int)
   */
  @Nullable
  public static PsiElement findTargetElement(Editor editor, int flags) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    int offset = editor.getCaretModel().getOffset();
    final PsiElement result = getInstance().findTargetElement(editor, flags, offset);
    if (result != null) return result;

    int expectedCaretOffset = editor instanceof EditorEx ? ((EditorEx)editor).getExpectedCaretOffset() : offset;
    if (expectedCaretOffset != offset) {
      return getInstance().findTargetElement(editor, flags, expectedCaretOffset);
    }
    return null;
  }

  /**
   * Note: this method can perform slow PSI activity (e.g. {@link PsiReference#resolve()}, so please avoid calling it from Swing thread.
   * @param editor editor
   * @param flags a combination of {@link #REFERENCED_ELEMENT_ACCEPTED}, {@link #ELEMENT_NAME_ACCEPTED}, {@link #LOOKUP_ITEM_ACCEPTED}
   * @param offset offset in the editor's document           f? yt jlby dfh
   * @return a PSI element declared or referenced at the specified offset in the editor, or selected in the {@link Lookup} if shown in the editor,
   * depending on the flags passed.
   * @see #findTargetElement(Editor, int)
   */
  @Override
  @Nullable
  public PsiElement findTargetElement(@NotNull Editor editor, int flags, int offset) {
    PsiElement result = doFindTargetElement(editor, flags, offset);
    TargetElementEvaluatorEx2 evaluator = result != null ? getElementEvaluatorsEx2(result.getLanguage()) : null;
    if (evaluator != null) {
      result = evaluator.adjustTargetElement(editor, offset, flags, result);
    }
    return result;
  }

  @Nullable
  private PsiElement doFindTargetElement(@NotNull Editor editor, int flags, int offset) {
    Project project = editor.getProject();
    if (project == null) return null;

    if (BitUtil.isSet(flags, LOOKUP_ITEM_ACCEPTED)) {
      PsiElement element = getTargetElementFromLookup(project);
      if (element != null) {
        return element;
      }
    }

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    offset = adjustOffset(file, document, offset);

    PsiElement element = file.findElementAt(offset);
    if (BitUtil.isSet(flags, REFERENCED_ELEMENT_ACCEPTED)) {
      final PsiElement referenceOrReferencedElement = getReferenceOrReferencedElement(file, editor, flags, offset);
      //if (referenceOrReferencedElement == null) {
      //  return getReferenceOrReferencedElement(file, editor, flags, offset);
      //}
      if (isAcceptableReferencedElement(element, referenceOrReferencedElement)) {
        return referenceOrReferencedElement;
      }
    }

    if (element == null) return null;

    if (BitUtil.isSet(flags, ELEMENT_NAME_ACCEPTED)) {
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
      if (item != null && item.isValid()) {
        final PsiElement psi = CompletionUtil.getTargetElement(item);
        if (psi != null && psi.isValid()) {
          return psi;
        }
      }
    }
    return null;
  }

  protected boolean isAcceptableReferencedElement(@Nullable PsiElement element, @Nullable PsiElement referenceOrReferencedElement) {
    if (referenceOrReferencedElement == null || !referenceOrReferencedElement.isValid()) return false;

    TargetElementEvaluatorEx2 evaluator = element != null ? getElementEvaluatorsEx2(element.getLanguage()) : null;
    if (evaluator != null) {
      ThreeState answer = evaluator.isAcceptableReferencedElement(element, referenceOrReferencedElement);
      if (answer == ThreeState.YES) return true;
      if (answer == ThreeState.NO) return false;
    }

    return true;
  }

  @Override
  @Nullable
  public PsiElement adjustElement(final Editor editor, final int flags, @Nullable PsiElement element, @Nullable PsiElement contextElement) {
    PsiElement langElement = element == null ? contextElement : element;
    TargetElementEvaluatorEx2 evaluator = langElement != null ? getElementEvaluatorsEx2(langElement.getLanguage()) : null;
    if (evaluator != null) {
      element = evaluator.adjustElement(editor, flags, element, contextElement);
    }
    return element;
  }

  @Override
  @Nullable
  public PsiElement adjustReference(@NotNull PsiReference ref) {
    PsiElement element = ref.getElement();
    TargetElementEvaluatorEx2 evaluator = element != null ? getElementEvaluatorsEx2(element.getLanguage()) : null;
    return evaluator != null ? evaluator.adjustReference(ref) : null;
  }

  @Override
  @Nullable
  public PsiElement getNamedElement(@Nullable final PsiElement element, final int offsetInElement) {
    if (element == null) return null;

    final List<PomTarget> targets = ContainerUtil.newArrayList();
    final Consumer<PomTarget> consumer = target -> {
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
  private PsiElement getNamedElement(@Nullable final PsiElement element) {
    if (element == null) return null;

    TargetElementEvaluatorEx2 evaluator = getElementEvaluatorsEx2(element.getLanguage());
    if (evaluator != null) {
      PsiElement result = evaluator.getNamedElement(element);
      if (result != null) return result;
    }

    PsiElement parent;
    if ((parent = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false)) != null) {
      boolean isInjected = parent instanceof PsiFile
                           && InjectedLanguageManager.getInstance(parent.getProject()).isInjectedFragment((PsiFile)parent);
      // A bit hacky depends on navigation offset correctly overridden
      if (!isInjected && parent.getTextOffset() == element.getTextRange().getStartOffset()) {
        if (evaluator == null || evaluator.isAcceptableNamedParent(parent)) {
          return parent;
        }
      }
    }

    return null;
  }

  @Nullable
  private PsiElement getReferenceOrReferencedElement(PsiFile file, Editor editor, int flags, int offset) {
    PsiElement result = doGetReferenceOrReferencedElement(file, editor, flags, offset);
    PsiElement languageElement = file.findElementAt(offset);
    Language language = languageElement != null ? languageElement.getLanguage() : file.getLanguage();
    TargetElementEvaluatorEx2 evaluator = getElementEvaluatorsEx2(language);
    if (evaluator != null) {
      result = evaluator.adjustReferenceOrReferencedElement(file, editor, offset, flags, result);
    }
    return result;
  }

  @Nullable
  private PsiElement doGetReferenceOrReferencedElement(PsiFile file, Editor editor, int flags, int offset) {
    PsiReference ref = findReference(editor, offset);
    if (ref == null) return null;

    final Language language = ref.getElement().getLanguage();
    TargetElementEvaluator evaluator = targetElementEvaluator.forLanguage(language);
    if (evaluator != null) {
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

  @Override
  @NotNull
  public Collection<PsiElement> getTargetCandidates(@NotNull PsiReference reference) {
    PsiElement refElement = reference.getElement();
    TargetElementEvaluatorEx2 evaluator = refElement != null ? getElementEvaluatorsEx2(refElement.getLanguage()) : null;
    if (evaluator != null) {
      Collection<PsiElement> candidates = evaluator.getTargetCandidates(reference);
      if (candidates != null) return candidates;
    }

    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      List<PsiElement> navigatableResults = new ArrayList<>(results.length);

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

  @Override
  public PsiElement getGotoDeclarationTarget(final PsiElement element, final PsiElement navElement) {
    TargetElementEvaluatorEx2 evaluator = element != null ? getElementEvaluatorsEx2(element.getLanguage()) : null;
    if (evaluator != null) {
      PsiElement result = evaluator.getGotoDeclarationTarget(element, navElement);
      if (result != null) return result;
    }
    return navElement;
  }

  @Override
  public boolean includeSelfInGotoImplementation(@NotNull final PsiElement element) {
    TargetElementEvaluator evaluator = targetElementEvaluator.forLanguage(element.getLanguage());
    return evaluator == null || evaluator.includeSelfInGotoImplementation(element);
  }

  @Override
  public boolean acceptImplementationForReference(@Nullable PsiReference reference, @Nullable PsiElement element) {
    TargetElementEvaluatorEx2 evaluator = element != null ? getElementEvaluatorsEx2(element.getLanguage()) : null;
    return evaluator == null || evaluator.acceptImplementationForReference(reference, element);
  }

  @Override
  @NotNull
  public SearchScope getSearchScope(Editor editor, @NotNull PsiElement element) {
    TargetElementEvaluatorEx2 evaluator = getElementEvaluatorsEx2(element.getLanguage());
    SearchScope result = evaluator != null ? evaluator.getSearchScope(editor, element) : null;
    return result != null ? result : PsiSearchHelper.SERVICE.getInstance(element.getProject()).getUseScope(element);
  }

  protected final LanguageExtension<TargetElementEvaluator> targetElementEvaluator =
    new LanguageExtension<>("com.intellij.targetElementEvaluator");
  @Nullable
  private TargetElementEvaluatorEx getElementEvaluatorsEx(@NotNull Language language) {
    TargetElementEvaluator result = targetElementEvaluator.forLanguage(language);
    return result instanceof TargetElementEvaluatorEx ? (TargetElementEvaluatorEx)result : null;
  }
  @Nullable
  private TargetElementEvaluatorEx2 getElementEvaluatorsEx2(@NotNull Language language) {
    TargetElementEvaluator result = targetElementEvaluator.forLanguage(language);
    return result instanceof TargetElementEvaluatorEx2 ? (TargetElementEvaluatorEx2)result : null;
  }
}
