/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  private boolean isResolvable() {
    return myHiddenClass.getQualifiedName() != null;
  }

  public void resolveCollision() throws IncorrectOperationException {
    if (!myCollisionReference.isValid()) return;
    final PsiManager manager = myCollisionReference.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    if (!isResolvable()) return;
    final String qName = myHiddenClass.getQualifiedName();
    if (myCollisionReference instanceof PsiReferenceExpression) {
      myCollisionReference.replace(factory.createExpressionFromText(qName, myCollisionReference));
    } else {
      myCollisionReference.replace(factory.createFQClassNameReferenceElement(qName, myCollisionReference.getResolveScope()));
    }
  }
}
