/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 05.06.2002
 * Time: 12:27:26
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;

public abstract class ResolvableCollisionUsageInfo extends CollisionUsageInfo {
  public ResolvableCollisionUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }
}
