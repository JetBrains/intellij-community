package com.intellij.dupLocator.treeView;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Mar 20, 2004
 * Time: 12:31:53 PM
 * To change this template use File | Settings | File Templates.
 */
public interface NodeMatcher {
  boolean match(PsiElement node);
}
