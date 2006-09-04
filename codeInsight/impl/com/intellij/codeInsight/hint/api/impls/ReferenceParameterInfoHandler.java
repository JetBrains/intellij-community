package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.hint.api.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Feb 1, 2006
 * Time: 3:16:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class ReferenceParameterInfoHandler implements ParameterInfoHandler<PsiReferenceParameterList,PsiTypeParameter> {
  public Object[] getParametersForLookup(final LookupItem item, final ParameterInfoContext context) {
    return null;
  }

  public Object[] getParametersForDocumentation(final PsiTypeParameter p, final ParameterInfoContext context) {
    return new Object[] {p};
  }

  public boolean couldShowInLookup() {
    return false;
  }

  public PsiReferenceParameterList findElementForParameterInfo(final CreateParameterInfoContext context) {
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

  public void showParameterInfo(final PsiReferenceParameterList element, final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
  }

  public PsiReferenceParameterList findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context) {
    return ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiReferenceParameterList.class);
  }

  public void updateParameterInfo(final PsiReferenceParameterList o, final UpdateParameterInfoContext context) {
    int index = ParameterInfoUtils.getCurrentParameterIndex(o.getNode(), context.getOffset(), JavaTokenType.COMMA);
    context.setCurrentParameter(index);
    final Object[] objectsToView = context.getObjectsToView();
    context.setHighlightedParameter(index < objectsToView.length ? (PsiElement)objectsToView[index]:null);
  }

  @NotNull
  public String getParameterCloseChars() {
    return ",>";
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  public void updateUI(PsiTypeParameter o, ParameterInfoUIContext context) {
    updateTypeParameter(o, context);
  }

  private static void updateTypeParameter(PsiTypeParameter typeParameter, ParameterInfoUIContext context) {
    @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append(typeParameter.getName());
    int highlightEndOffset = buffer.length();
    buffer.append(" extends ");
    buffer.append(StringUtil.join(
      Arrays.asList(typeParameter.getSuperTypes()),
      new Function<PsiClassType, String>() {
        public String fun(final PsiClassType t) {
          return t.getPresentableText();
        }
      }, ", "));

    context.setupUIComponentPresentation(buffer.toString(), 0, highlightEndOffset, false, false, false,
                                         context.getDefaultParameterColor());
  }
}
