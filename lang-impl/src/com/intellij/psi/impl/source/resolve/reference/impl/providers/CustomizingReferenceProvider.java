package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 9, 2005
 * Time: 8:10:06 PM
 * To change this template use File | Settings | File Templates.
 */
public class CustomizingReferenceProvider implements CustomizableReferenceProvider {
  private CustomizableReferenceProvider myProvider;
  private @Nullable Map<CustomizationKey, Object> myOptions;

  public CustomizingReferenceProvider(@NotNull CustomizableReferenceProvider provider) {
    myProvider = provider;
  }
  
  public <Option> void addCustomization(CustomizationKey<Option> key, Option value) {
    if (myOptions == null) {
      myOptions = new HashMap<CustomizationKey, Object>(5);
    }
    myOptions.put(key,value);
  }
  
  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    myProvider.setOptions(myOptions);
    final PsiReference[] referencesByElement = myProvider.getReferencesByElement(element);
    myProvider.setOptions(null);
    return referencesByElement;
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, int offsetInPosition) {
    myProvider.setOptions(myOptions);
    final PsiReference[] referencesByElement = myProvider.getReferencesByString(str,position, offsetInPosition);
    myProvider.setOptions(null);
    return referencesByElement;
  }

  public void setOptions(@Nullable Map<CustomizationKey, Object> options) {
    myOptions = options;  // merge ?
  }

  @Nullable
  public Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }
}
