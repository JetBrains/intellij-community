/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;

/**
 * changes 'class a extends b' to 'class a implements b' or vice versa
 */
public class ChangeExtendsToImplementsFix extends ExtendsListFix {
  public ChangeExtendsToImplementsFix(PsiClass aClass, PsiClassType classToExtendFrom) {
    super(aClass, classToExtendFrom, true);
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("exchange.extends.implements.keyword",
          myClass.isInterface() == myClassToExtendFrom.isInterface() ? PsiKeyword.IMPLEMENTS : PsiKeyword.EXTENDS,
          myClass.isInterface() == myClassToExtendFrom.isInterface() ? PsiKeyword.EXTENDS : PsiKeyword.IMPLEMENTS,
          myClassToExtendFrom.getQualifiedName());
  }
}