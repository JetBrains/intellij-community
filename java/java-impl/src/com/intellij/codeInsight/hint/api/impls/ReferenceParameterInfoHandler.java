// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.hint.api.JavaParameterInfo;
import com.intellij.codeInsight.hint.api.JavaParameterPresentation;
import com.intellij.codeInsight.hint.api.JavaSignaturePresentation;
import com.intellij.codeInsight.hint.api.ReadOnlyJavaParameterInfoHandler;
import com.intellij.codeInsight.hint.api.ReadOnlyJavaParameterUpdateInfoContext;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.ParameterInfoUtils;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.util.Range;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public final class ReferenceParameterInfoHandler implements ParameterInfoHandler<PsiReferenceParameterList,PsiTypeParameter>,
                                                            ReadOnlyJavaParameterInfoHandler {

  @Override
  public PsiReferenceParameterList findElementForParameterInfo(final @NotNull CreateParameterInfoContext context) {
    final PsiReferenceParameterList referenceParameterList =
      ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiReferenceParameterList.class);

    if (referenceParameterList != null) {
      final PsiTypeParameter[] typeParams = getTypeParameters(referenceParameterList);
      if (typeParams == null) return null;

      context.setItemsToShow(typeParams);
      return referenceParameterList;
    }

    return null;
  }

  private static @NotNull PsiTypeParameter @Nullable [] getTypeParameters(PsiReferenceParameterList referenceParameterList) {
    if (!(referenceParameterList.getParent() instanceof PsiJavaCodeReferenceElement ref)) return null;
    final PsiElement psiElement = ref.resolve();
    if (!(psiElement instanceof PsiTypeParameterListOwner)) return null;

    final PsiTypeParameter[] typeParams = ((PsiTypeParameterListOwner)psiElement).getTypeParameters();
    if (typeParams.length == 0) return null;
    return typeParams;
  }

  @Override
  public void showParameterInfo(final @NotNull PsiReferenceParameterList element, final @NotNull CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Override
  public PsiReferenceParameterList findElementForUpdatingParameterInfo(final @NotNull UpdateParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiReferenceParameterList.class);
  }

  @Override
  public void updateParameterInfo(final @NotNull PsiReferenceParameterList parameterOwner, final @NotNull UpdateParameterInfoContext context) {
    int index = ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.getNode(), context.getOffset(), JavaTokenType.COMMA);
    context.setCurrentParameter(index);
    final Object[] objectsToView = context.getObjectsToView();
    context.setHighlightedParameter(index < objectsToView.length && index >= 0 ? objectsToView[index] : null);
  }

  @Override
  public void updateUI(PsiTypeParameter o, @NotNull ParameterInfoUIContext context) {
    updateTypeParameter(o, context);
  }

  @Override
  public @Nullable JavaParameterInfo getParameterInfo(@NotNull PsiFile file, int offset) {
    final PsiReferenceParameterList referenceParameterList =
      ParameterInfoUtils.findParentOfType(file, offset, PsiReferenceParameterList.class);
    if (referenceParameterList == null) return null;

    PsiTypeParameter[] typeParameters = getTypeParameters(referenceParameterList);
    if (typeParameters == null) return null;

    ReadOnlyJavaParameterUpdateInfoContext context = new ReadOnlyJavaParameterUpdateInfoContext(file, typeParameters, offset);
    updateParameterInfo(referenceParameterList, context);

    return createLightJavaParameterInfo(typeParameters, context);
  }

  private static @NotNull JavaParameterInfo createLightJavaParameterInfo(PsiTypeParameter @NotNull [] typeParameters,
                                                                         ReadOnlyJavaParameterUpdateInfoContext context) {
    List<@NotNull UIPresentation> parameterPresentation =
      ContainerUtil.map(typeParameters, typeParameter -> getTypeParameterPresentation(typeParameter));

    StringBuilder buffer = new StringBuilder();
    List<JavaParameterPresentation> parameterPresentationList = new ArrayList<>(parameterPresentation.size());

    for (int i = 0; i < typeParameters.length; i++) {
      int highlightStartOffset = buffer.length();
      parameterPresentationList.add(new JavaParameterPresentation(
        new Range<>(highlightStartOffset, highlightStartOffset + parameterPresentation.get(i).highlightEndOffset), null)
      );
      buffer.append(parameterPresentation.get(i).label());
      if (i < typeParameters.length - 1) buffer.append(", ");
    }

    return new JavaParameterInfo(
      List.of(new JavaSignaturePresentation(buffer.toString(), parameterPresentationList, context.getCurrentParameterIndex())),
      null,
      context.getCurrentParameterIndex()
    );
  }

  private static void updateTypeParameter(PsiTypeParameter typeParameter, ParameterInfoUIContext context) {
    UIPresentation presentation = getTypeParameterPresentation(typeParameter);

    context.setupUIComponentPresentation(presentation.label(), 0, presentation.highlightEndOffset(), false, false, false,
                                         context.getDefaultParameterColor());
  }

  private static @NotNull UIPresentation getTypeParameterPresentation(PsiTypeParameter typeParameter) {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(typeParameter.getName());
    int highlightEndOffset = buffer.length();
    buffer.append(" extends ");
    buffer.append(StringUtil.join(
      Arrays.asList(typeParameter.getSuperTypes()),
      t -> t.getPresentableText(), ", "));
    UIPresentation presentation = new UIPresentation(buffer.toString(), highlightEndOffset);
    return presentation;
  }

  private record UIPresentation(String label, int highlightEndOffset) {
  }
}
