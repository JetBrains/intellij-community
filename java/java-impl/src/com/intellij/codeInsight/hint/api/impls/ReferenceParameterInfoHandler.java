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
package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author Maxim.Mossienko
 */
public class ReferenceParameterInfoHandler implements ParameterInfoHandler<PsiReferenceParameterList,PsiTypeParameter> {
  @Override
  public Object[] getParametersForLookup(final LookupElement item, final ParameterInfoContext context) {
    return null;
  }

  @Override
  public boolean couldShowInLookup() {
    return false;
  }

  @Override
  public PsiReferenceParameterList findElementForParameterInfo(@NotNull final CreateParameterInfoContext context) {
    final PsiReferenceParameterList referenceParameterList =
      ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiReferenceParameterList.class);

    if (referenceParameterList != null) {
      if (!(referenceParameterList.getParent() instanceof PsiJavaCodeReferenceElement)) return null;
      final PsiJavaCodeReferenceElement ref = ((PsiJavaCodeReferenceElement)referenceParameterList.getParent());
      final PsiElement psiElement = ref.resolve();
      if (!(psiElement instanceof PsiTypeParameterListOwner)) return null;

      final PsiTypeParameter[] typeParams = ((PsiTypeParameterListOwner)psiElement).getTypeParameters();
      if (typeParams.length == 0) return null;

      context.setItemsToShow(typeParams);
      return referenceParameterList;
    }

    return null;
  }

  @Override
  public void showParameterInfo(@NotNull final PsiReferenceParameterList element, @NotNull final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  @Override
  public PsiReferenceParameterList findElementForUpdatingParameterInfo(@NotNull final UpdateParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiReferenceParameterList.class);
  }

  @Override
  public void updateParameterInfo(@NotNull final PsiReferenceParameterList parameterOwner, @NotNull final UpdateParameterInfoContext context) {
    int index = ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.getNode(), context.getOffset(), JavaTokenType.COMMA);
    context.setCurrentParameter(index);
    final Object[] objectsToView = context.getObjectsToView();
    context.setHighlightedParameter(index < objectsToView.length && index >= 0 ? (PsiElement)objectsToView[index]:null);
  }

  @Override
  public void updateUI(PsiTypeParameter o, @NotNull ParameterInfoUIContext context) {
    updateTypeParameter(o, context);
  }

  private static void updateTypeParameter(PsiTypeParameter typeParameter, ParameterInfoUIContext context) {
    @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append(typeParameter.getName());
    int highlightEndOffset = buffer.length();
    buffer.append(" extends ");
    buffer.append(StringUtil.join(
      Arrays.asList(typeParameter.getSuperTypes()),
      t -> t.getPresentableText(), ", "));

    context.setupUIComponentPresentation(buffer.toString(), 0, highlightEndOffset, false, false, false,
                                         context.getDefaultParameterColor());
  }
}
