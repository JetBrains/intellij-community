// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.hint.api.JavaParameterInfo;
import com.intellij.codeInsight.hint.api.ReadOnlyJavaParameterInfoHandler;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.ParameterInfoUtils;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public final class AnnotationParameterInfoHandler implements ParameterInfoHandler<PsiAnnotationParameterList,PsiAnnotationMethod>,
                                                             ReadOnlyJavaParameterInfoHandler, DumbAware {

  @Override
  public PsiAnnotationParameterList findElementForParameterInfo(final @NotNull CreateParameterInfoContext context) {
    final PsiAnnotation annotation = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiAnnotation.class);
    if (annotation == null) return null;

    PsiClass aClass = annotation.resolveAnnotationType();
    if (aClass == null) return null;

    final PsiMethod[] methods = aClass.getMethods();
    if (methods.length == 0) return null;

    context.setItemsToShow(methods);

    final PsiAnnotationMethod annotationMethod = AnnotationParameterInfoUtil.findAnnotationMethod(context.getFile(), context.getOffset());
    if (annotationMethod != null) context.setHighlightedElement(annotationMethod);

    return annotation.getParameterList();
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
    context.setHighlightedParameter(AnnotationParameterInfoUtil.findAnnotationMethod(context.getFile(), offset1));
  }

  @Override
  public void updateUI(final PsiAnnotationMethod p, final @NotNull ParameterInfoUIContext context) {
    updateUIText(p, context);
  }

  @Override
  public @Nullable JavaParameterInfo getParameterInfo(@NotNull PsiFile file, int offset) {
    final PsiAnnotation annotation = ParameterInfoUtils.findParentOfType(file, offset, PsiAnnotation.class);
    if (annotation == null) return null;

    PsiClass aClass = annotation.resolveAnnotationType();
    if (aClass == null) return null;

    PsiAnnotationParameterList attributeList = ParameterInfoUtils.findParentOfType(file, offset, PsiAnnotationParameterList.class);
    if (attributeList == null) return null;
    PsiNameValuePair[] attributes = attributeList.getAttributes();

    List<PsiAnnotationMethod> methodList = ContainerUtil.filterIsInstance(aClass.getMethods(), PsiAnnotationMethod.class);

    return AnnotationParameterInfoUtil.createLightJavaParameterInfo(file, offset, methodList, attributes);
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
}
