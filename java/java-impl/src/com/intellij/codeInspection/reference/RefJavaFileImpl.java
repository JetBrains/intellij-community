/*
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiJavaFile;

public class RefJavaFileImpl extends RefFileImpl {
  RefJavaFileImpl(PsiJavaFile elem, RefManager manager) {
    super(elem, manager);
    ((RefPackageImpl)getRefManager().getExtension(RefJavaManager.MANAGER).getPackage(((PsiJavaFile)elem).getPackageName())).add(this);
  }
}