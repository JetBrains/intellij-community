/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.move.moveInner;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

public class MoveInnerOptions {
  private final PsiClass myInnerClass;
  private final PsiClass myOuterClass;
  private final PsiElement myTargetContainer;
  private final String myNewClassName;

  public MoveInnerOptions(final PsiClass innerClass,
                          final PsiClass outerClass,
                          final PsiElement targetContainer,
                          final String newClassName) {
    myInnerClass = innerClass;
    myOuterClass = outerClass;
    myTargetContainer = targetContainer;
    myNewClassName = newClassName;
  }

  public PsiClass getInnerClass() {
    return myInnerClass;
  }

  public PsiClass getOuterClass() {
    return myOuterClass;
  }

  public PsiElement getTargetContainer() {
    return myTargetContainer;
  }

  public String getNewClassName() {
    return myNewClassName;
  }
}
