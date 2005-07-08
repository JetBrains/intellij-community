package com.intellij.lang;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class EmptyFindUsagesProvider implements FindUsagesProvider {
  public boolean mayHaveReferences(IElementType token, final short searchContext) {
    return false;
  }

  @Nullable
  public WordsScanner getWordsScanner() {
    return null;
  }

  public boolean canFindUsagesFor(PsiElement psiElement) {
    return false;
  }

  @Nullable
  public String getHelpId(PsiElement psiElement) {
    return null;
  }

  public String getType(PsiElement element) {
    return "";
  }

  public String getDescriptiveName(PsiElement element) {
    //do not return null
    return element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : "";
  }

  public String getNodeText(PsiElement element, boolean useFullName) {
    //do not return null
    return element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : "";
  }
}
