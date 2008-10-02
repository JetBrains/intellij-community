package com.intellij.util.xml;

import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.Collection;

/**
 * @author Gregory.Shrago
 */
public class DomNameSuggestionProvider implements NameSuggestionProvider {
  public SuggestedNameInfo getSuggestedNames(final PsiElement element, final PsiElement nameSuggestionContext, final List<String> result) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData psiMetaData = ((PsiMetaOwner)element).getMetaData();
      if (psiMetaData instanceof DomMetaData) {
        final DomMetaData domMetaData = (DomMetaData)psiMetaData;
        final GenericDomValue value = domMetaData.getNameElement(domMetaData.getElement());
        ContainerUtil.addIfNotNull(ElementPresentationManager.getNameFromNameValue(value, true), result);
      }
    }
    return null;
  }

  public Collection<LookupElement> completeName(final PsiElement element, final PsiElement nameSuggestionContext, final String prefix) {
    return null;
  }
}
