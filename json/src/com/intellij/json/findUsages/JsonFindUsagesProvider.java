package com.intellij.json.findUsages;

import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.HelpID;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonFindUsagesProvider implements FindUsagesProvider {
  @Nullable
  @Override
  public WordsScanner getWordsScanner() {
    return new JsonWordScanner();
  }

  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement;
  }

  @Nullable
  @Override
  public String getHelpId(@NotNull PsiElement psiElement) {
    return HelpID.FIND_OTHER_USAGES;
  }

  @NotNull
  @Override
  public String getType(@NotNull PsiElement element) {
    if (element instanceof JsonProperty) {
      return "property";
    }
    return "";
  }

  @NotNull
  @Override
  public String getDescriptiveName(@NotNull PsiElement element) {
    final String name = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : null;
    return name != null ? name : "<unnamed>";
  }

  @NotNull
  @Override
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}
