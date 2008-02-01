package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.patterns.MatchingContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
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
public class CustomizingReferenceProvider extends PsiReferenceProvider implements CustomizableReferenceProvider {
  private CustomizableReferenceProvider myProvider;
  private @Nullable Map<CustomizableReferenceProvider.CustomizationKey, Object> myOptions;

  public CustomizingReferenceProvider(@NotNull CustomizableReferenceProvider provider) {
    myProvider = provider;
  }
  
  public <Option> void addCustomization(CustomizableReferenceProvider.CustomizationKey<Option> key, Option value) {
    if (myOptions == null) {
      myOptions = new HashMap<CustomizableReferenceProvider.CustomizationKey, Object>(5);
    }
    myOptions.put(key,value);
  }
  
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final MatchingContext matchingContext) {
    myProvider.setOptions(myOptions);
    final PsiReference[] referencesByElement = myProvider.getReferencesByElement(element, matchingContext);
    myProvider.setOptions(null);
    return referencesByElement;
  }

  public void setOptions(@Nullable Map<CustomizableReferenceProvider.CustomizationKey, Object> options) {
    myOptions = options;  // merge ?
  }

  @Nullable
  public Map<CustomizationKey, Object> getOptions() {
    return myOptions;
  }
}
