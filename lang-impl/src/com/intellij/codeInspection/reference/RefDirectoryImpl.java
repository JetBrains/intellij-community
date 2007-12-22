/*
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;

public class RefDirectoryImpl extends RefElementImpl implements RefDirectory{
  protected RefDirectoryImpl(String name, PsiElement psiElement, RefManager refManager) {
    super(name, psiElement, refManager);
  }

  public void accept(final RefVisitor visitor) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        visitor.visitDirectory(RefDirectoryImpl.this);
      }
    });
  }

  protected void initialize() {
    getRefManager().fireNodeInitialized(this);
  }
}