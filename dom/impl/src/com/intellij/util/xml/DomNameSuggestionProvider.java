package com.intellij.util.xml;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

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
        ContainerUtil.addIfNotNull(getNameFromNameValue(value, true), result);
      }
    }
    return null;
  }

  public Collection<LookupElement> completeName(final PsiElement element, final PsiElement nameSuggestionContext, final String prefix) {
    return null;
  }

  @Nullable
  private static String getNameFromNameValue(final Object o, final boolean local) {
    if (o == null || o instanceof String) {
      return (String)o;
    }
    else if (o instanceof GenericValue) {
      final GenericValue value = (GenericValue)o;
      if (!local) {
        final Object name = value.getValue();
        if (name != null) {
          return String.valueOf(name);
        }
      }
      return value.getStringValue();
    }
    else {
      return String.valueOf(o);
    }
  }
}
