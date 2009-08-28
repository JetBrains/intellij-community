package com.intellij.refactoring.rename;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public interface NameSuggestionProvider {
  ExtensionPointName<NameSuggestionProvider> EP_NAME = ExtensionPointName.create("com.intellij.nameSuggestionProvider");

  @Nullable
  SuggestedNameInfo getSuggestedNames(PsiElement element, PsiElement nameSuggestionContext, List<String> result);

  @Nullable
  Collection<LookupElement> completeName(PsiElement element, final PsiElement nameSuggestionContext, final String prefix);
}
