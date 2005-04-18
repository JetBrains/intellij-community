package com.intellij.psi.meta;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 18, 2005
 * Time: 2:41:47 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PsiWritableMetaData extends PsiMetaData {
  void setName(String name) throws IncorrectOperationException;
}
