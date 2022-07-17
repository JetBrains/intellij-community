// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTreeUtilKt;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.BitUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TargetElementUtilBase {

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
   * Attempts to adjust the {@code offset} in the {@code file} to point to an {@link #isIdentifierPart(PsiFile, CharSequence, int) identifier},
   * single quote, double quote, closing bracket or parentheses by moving it back by a single character. Does nothing if there are no
   * identifiers around, or the {@code offset} is already in one.
   *
   * @param file language source for the {@link #isIdentifierPart(PsiFile, CharSequence, int)}
   * @see PsiTreeUtilKt#elementsAroundOffsetUp(PsiFile, int)
   */
  public static int adjustOffset(@Nullable PsiFile file, @NotNull Document document, final int offset) {
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = text.length();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!isIdentifierPart(file, text, offset)) {
      correctedOffset--;
    }
    if (correctedOffset >= 0) {
      char charAt = text.charAt(correctedOffset);
      if (charAt == '\'' || charAt == '"' || charAt == ')' || charAt == ']' ||
          isIdentifierPart(file, text, correctedOffset)) {
        return correctedOffset;
      }
    }
    return offset;
  }

  /**
   * @return true iff character at the offset may be a part of an identifier.
   * @see Character#isJavaIdentifierPart(char)
   * @see TargetElementEvaluatorEx#isIdentifierPart(PsiFile, CharSequence, int)
   */
  private static boolean isIdentifierPart(@Nullable PsiFile file, @NotNull CharSequence text, int offset) {
    if (file != null) {
      TargetElementEvaluatorEx evaluator = getElementEvaluatorsEx(file.getLanguage());
      if (evaluator != null && evaluator.isIdentifierPart(file, text, offset)) return true;
    }
    return Character.isJavaIdentifierPart(text.charAt(offset));
  }

  static final LanguageExtension<TargetElementEvaluator> TARGET_ELEMENT_EVALUATOR =
    new LanguageExtension<>("com.intellij.targetElementEvaluator");

  @Nullable
  private static TargetElementEvaluatorEx getElementEvaluatorsEx(@NotNull Language language) {
    TargetElementEvaluator result = TARGET_ELEMENT_EVALUATOR.forLanguage(language);
    return result instanceof TargetElementEvaluatorEx ? (TargetElementEvaluatorEx)result : null;
  }

  @Nullable
  static TargetElementEvaluatorEx2 getElementEvaluatorsEx2(@NotNull Language language) {
    TargetElementEvaluator result = TARGET_ELEMENT_EVALUATOR.forLanguage(language);
    return result instanceof TargetElementEvaluatorEx2 ? (TargetElementEvaluatorEx2)result : null;
  }

  @ApiStatus.Internal
  static PsiElement getNamedElement(@Nullable PsiElement element) {
    if (element == null) return null;

    TargetElementEvaluatorEx2 evaluator = getElementEvaluatorsEx2(element.getLanguage());
    if (evaluator != null) {
      PsiElement result = evaluator.getNamedElement(element);
      if (result != null) return result;
    }

    PsiElement parent;
    if ((parent = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false)) != null) {
      // A bit hacky: depends on the named element's text offset being overridden correctly
      if (!(parent instanceof PsiFile) && parent.getTextOffset() == element.getTextRange().getStartOffset()) {
        if (evaluator == null || evaluator.isAcceptableNamedParent(parent)) {
          return parent;
        }
      }
    }

    return null;
  }

  public static PsiElement getNamedElement(@Nullable PsiElement element, int offsetInElement) {
    if (element == null) return null;

    PsiUtilCore.ensureValid(element);

    final List<PomTarget> targets = new ArrayList<>();
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
    while (parent != null && !(parent instanceof PsiFileSystemItem)) {
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
  private static PsiElement doGetReferenceOrReferencedElement(@NotNull Editor editor, int flags, int offset) {
    PsiReference ref = findReference(editor, offset);
    if (ref == null) return null;

    Project project = editor.getProject();
    if (project == null) return null;
    return getReferencedElement(ref, flags);
  }

  private static @Nullable PsiElement getReferencedElement(@NotNull PsiReference ref, int flags) {
    final Language language = ref.getElement().getLanguage();
    TargetElementEvaluator evaluator = TARGET_ELEMENT_EVALUATOR.forLanguage(language);
    if (evaluator != null) {
      final PsiElement element = evaluator.getElementByReference(ref, flags);
      if (element != null) {
        return element;
      }
    }
    return ref.resolve();
  }

  @Nullable
  public static PsiReference findReferenceWithoutExpectedCaret(@NotNull Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    return findReference(editor, offset);
  }

  @Nullable
  public static PsiReference findReference(@NotNull Editor editor, int offset) {
    Project project = editor.getProject();
    if (project == null) return null;

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    PsiReference ref = file.findReferenceAt(adjustOffset(file, document, offset));
    if (ref == null) return null;
    int elementOffset = ref.getElement().getTextRange().getStartOffset();

    for (TextRange range : ReferenceRange.getRanges(ref)) {
      if (range.shiftRight(elementOffset).containsOffset(offset)) {
        return ref;
      }
    }

    return null;
  }

  @Nullable
  private static PsiElement getReferenceOrReferencedElement(@NotNull PsiFile file, @NotNull Editor editor, int flags, int offset) {
    PsiElement result = doGetReferenceOrReferencedElement(editor, flags, offset);
    PsiElement languageElement = file.findElementAt(offset);
    Language language = languageElement != null ? languageElement.getLanguage() : file.getLanguage();
    TargetElementEvaluatorEx2 evaluator = getElementEvaluatorsEx2(language);
    if (evaluator != null) {
      result = evaluator.adjustReferenceOrReferencedElement(file, editor, offset, flags, result);
    }
    return result;
  }

  @Nullable
  private static PsiElement doFindTargetElement(@NotNull Editor editor, int flags, int offset) {
    Project project = editor.getProject();
    if (project == null) return null;

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    int adjusted = adjustOffset(file, document, offset);

    PsiElement element = file.findElementAt(adjusted);
    if (BitUtil.isSet(flags, REFERENCED_ELEMENT_ACCEPTED)) {
      final PsiElement referencedElement = getReferencedElement(file, offset, flags, editor, element);
      if (referencedElement != null) {
        return referencedElement;
      }
    }

    if (element == null) return null;

    if (BitUtil.isSet(flags, ELEMENT_NAME_ACCEPTED)) {
      if (element instanceof PsiNamedElement) return element;
      return getNamedElement(element, adjusted - element.getTextRange().getStartOffset());
    }
    return null;
  }

  @Nullable
  private static PsiElement getReferencedElement(@NotNull PsiFile file,
                                                 int offset,
                                                 int flags,
                                                 @NotNull Editor editor,
                                                 @Nullable PsiElement leafElement) {
    final PsiElement referenceOrReferencedElement = getReferenceOrReferencedElement(file, editor, flags, offset);
    if (isAcceptableReferencedElement(leafElement, referenceOrReferencedElement)) {
      return referenceOrReferencedElement;
    }
    return null;
  }

  private static boolean isAcceptableReferencedElement(@Nullable PsiElement element, @Nullable PsiElement referenceOrReferencedElement) {
    if (referenceOrReferencedElement == null || !referenceOrReferencedElement.isValid()) return false;

    TargetElementEvaluatorEx2 evaluator = element == null ? null : getElementEvaluatorsEx2(element.getLanguage());
    if (evaluator != null) {
      ThreeState answer = evaluator.isAcceptableReferencedElement(element, referenceOrReferencedElement);
      if (answer == ThreeState.YES) return true;
      if (answer == ThreeState.NO) return false;
    }

    return true;
  }

  /**
   * Note: this method can perform slow PSI activity (e.g. {@link PsiReference#resolve()}, so please avoid calling it from Swing thread.
   *
   * @param editor editor
   * @param flags  a combination of {@link #REFERENCED_ELEMENT_ACCEPTED}, {@link #ELEMENT_NAME_ACCEPTED}
   * @return a PSI element declared or referenced at the specified offset in the editor, depending on the flags passed.
   * @see #findTargetElement(Editor, int, int)
   */
  @Nullable
  public static PsiElement findTargetElement(@NotNull Editor editor, int flags) {
    int offset = editor.getCaretModel().getOffset();
    return findTargetElement(editor, flags, offset);
  }

  /**
   * Note: this method can perform slow PSI activity (e.g. {@link PsiReference#resolve()}, so please avoid calling it from Swing thread.
   * @param editor editor
   * @param flags a combination of {@link #REFERENCED_ELEMENT_ACCEPTED}, {@link #ELEMENT_NAME_ACCEPTED}
   * @param offset offset in the editor's document
   * @return a PSI element declared or referenced at the specified offset in the editor, depending on the flags passed.
   * @see #findTargetElement(Editor, int)
   */
  @Nullable
  public static PsiElement findTargetElement(@NotNull Editor editor, int flags, int offset) {
    PsiElement result = doFindTargetElement(editor, flags, offset);
    TargetElementEvaluatorEx2 evaluator = result != null ? getElementEvaluatorsEx2(result.getLanguage()) : null;
    if (evaluator != null) {
      result = evaluator.adjustTargetElement(editor, offset, flags, result);
    }
    return result;
  }
}