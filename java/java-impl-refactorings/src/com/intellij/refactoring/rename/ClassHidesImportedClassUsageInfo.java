// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class ClassHidesImportedClassUsageInfo extends ResolvableCollisionUsageInfo {
  private final PsiClass myHiddenClass;
  private final PsiJavaCodeReferenceElement myCollisionReference;

  public ClassHidesImportedClassUsageInfo(PsiJavaCodeReferenceElement collisionReference, PsiClass renamedClass, PsiClass hiddenClass) {
    super(collisionReference, renamedClass);
    myHiddenClass = hiddenClass;
    myCollisionReference = collisionReference;
  }

  public void resolveCollision() throws IncorrectOperationException {
    if (!myCollisionReference.isValid()) return;
    final PsiManager manager = myCollisionReference.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());

    String qualifiedName = myHiddenClass.getQualifiedName();
    if (qualifiedName == null) return;
    if (myCollisionReference instanceof PsiReferenceExpression) {
      myCollisionReference.replace(factory.createExpressionFromText(qualifiedName, myCollisionReference));
    } else {
      myCollisionReference.bindToElement(myHiddenClass);
    }
  }
}
