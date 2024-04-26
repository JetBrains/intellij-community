// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public final class AnnotationParameterInfoHandler implements ParameterInfoHandler<PsiAnnotationParameterList,PsiAnnotationMethod>, DumbAware {

  @Override
  public PsiAnnotationParameterList findElementForParameterInfo(final @NotNull CreateParameterInfoContext context) {
    final PsiAnnotation annotation = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiAnnotation.class);

    if (annotation != null) {
      PsiClass aClass = annotation.resolveAnnotationType();
      if (aClass != null) {
        final PsiMethod[] methods = aClass.getMethods();

        if (methods.length != 0) {
          context.setItemsToShow(methods);

          final PsiAnnotationMethod annotationMethod = findAnnotationMethod(context.getFile(), context.getOffset());
          if (annotationMethod != null) context.setHighlightedElement(annotationMethod);

          return annotation.getParameterList();
        }
      }
    }

    return null;
  }

  @Override
  public void showParameterInfo(final @NotNull PsiAnnotationParameterList element, final @NotNull CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Override
  public PsiAnnotationParameterList findElementForUpdatingParameterInfo(final @NotNull UpdateParameterInfoContext context) {
    final PsiAnnotation annotation = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiAnnotation.class);
    return annotation != null ? annotation.getParameterList() : null;
  }

  @Override
  public void updateParameterInfo(final @NotNull PsiAnnotationParameterList parameterOwner, final @NotNull UpdateParameterInfoContext context) {
    CharSequence chars = context.getEditor().getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftForward(chars, context.getEditor().getCaretModel().getOffset(), " \t");
    final char c = chars.charAt(offset1);
    if (c == ',' || c == ')') {
      offset1 = CharArrayUtil.shiftBackward(chars, offset1 - 1, " \t");
    }
    context.setHighlightedParameter(findAnnotationMethod(context.getFile(), offset1));
  }

  @Override
  public void updateUI(final PsiAnnotationMethod p, final @NotNull ParameterInfoUIContext context) {
    updateUIText(p, context);
  }

  public static String updateUIText(PsiAnnotationMethod p, ParameterInfoUIContext context) {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(p.getReturnType().getPresentableText());
    buffer.append(" ");
    int highlightStartOffset = buffer.length();
    buffer.append(p.getName());
    int highlightEndOffset = buffer.length();
    buffer.append("()");

    if (p.getDefaultValue() != null) {
      buffer.append(" default ");
      buffer.append(p.getDefaultValue().getText());
    }

    return context.setupUIComponentPresentation(buffer.toString(), highlightStartOffset, highlightEndOffset, false, p.isDeprecated(),
                                                false, context.getDefaultParameterColor());
  }

  private static PsiAnnotationMethod findAnnotationMethod(PsiFile file, int offset) {
    PsiNameValuePair pair = ParameterInfoUtils.findParentOfType(file, offset, PsiNameValuePair.class);
    if (pair == null) return null;
    final PsiReference reference = pair.getReference();
    final PsiElement resolved = reference != null ? reference.resolve():null;
    return PsiUtil.isAnnotationMethod(resolved) ? (PsiAnnotationMethod)resolved : null;
  }
}
