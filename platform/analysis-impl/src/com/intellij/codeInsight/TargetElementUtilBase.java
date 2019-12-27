// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PsiDeclaredTarget;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTreeUtilKt;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TargetElementUtilBase {

  /**
   * Attempts to adjust the {@code offset} in the {@code file} to point to an {@link #isIdentifierPart(PsiFile, CharSequence, int) identifier},
   * single quote, double quote, closing bracket or parentheses by moving it back by a single character. Does nothing if there are no
   * identifiers around, or the {@code offset} is already in one.
   *
   * @param file language source for the {@link #isIdentifierPart(PsiFile, CharSequence, int)}
   * @see PsiTreeUtilKt#elementsAroundOffsetUp(PsiFile, int)
   */
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
  private static boolean isIdentifierPart(@Nullable PsiFile file, CharSequence text, int offset) {
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
}
