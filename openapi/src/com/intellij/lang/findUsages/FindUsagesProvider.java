package com.intellij.lang.findUsages;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 14, 2005
 * Time: 5:48:12 PM
 * To change this template use File | Settings | File Templates.
 */
public interface FindUsagesProvider {
  boolean canFindUsagesFor(PsiElement psiElement);
  String getHelpId(PsiElement psiElement);

  String getType(PsiElement element);
  String getDescriptiveName(PsiElement element);
  String getNodeText(PsiElement element, boolean useFullName);
}
