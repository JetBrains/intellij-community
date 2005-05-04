package com.intellij.lang.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.cacheBuilder.WordsScanner;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 14, 2005
 * Time: 5:48:12 PM
 * To change this template use File | Settings | File Templates.
 */
public interface FindUsagesProvider {
  /**
   * @param token to check for references
   * @param searchContext represents find usages request,
   * a combination of constants in {@link com.intellij.psi.search.UsageSearchContext}
   */
  boolean mayHaveReferences(IElementType token, final short searchContext);

  /**
   * @return word scanner for building caches in this language's files
   */
  WordsScanner getWordsScanner();

  /**
   * @param psiElement
   * @return true if it is sensible to searh for usages of psiElement
   */
  boolean canFindUsagesFor(PsiElement psiElement);

  String getHelpId(PsiElement psiElement);

  String getType(PsiElement element);

  String getDescriptiveName(PsiElement element);

  String getNodeText(PsiElement element, boolean useFullName);
}
