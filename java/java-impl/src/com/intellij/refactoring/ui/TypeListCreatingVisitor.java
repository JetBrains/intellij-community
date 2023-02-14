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
package com.intellij.refactoring.ui;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiType;
import com.intellij.util.CommonJavaRefactoringUtil;

import java.util.ArrayList;
import java.util.HashSet;

class TypeListCreatingVisitor implements CommonJavaRefactoringUtil.SuperTypeVisitor {
  private final ArrayList<? super PsiType> myList;
  private final PsiElementFactory myFactory;
  private final HashSet<PsiType> mySet;

  TypeListCreatingVisitor(ArrayList<? super PsiType> result, PsiElementFactory factory) {
    myList = result;
    myFactory = factory;
    mySet = new HashSet<>();
  }

  @Override
  public void visitType(PsiType aType) {
    if (!mySet.contains(aType)) {
      myList.add(aType);
      mySet.add(aType);
    }
  }

  @Override
  public void visitClass(PsiClass aClass) {
    final PsiType type = myFactory.createType(aClass);
    if (!mySet.contains(type)) {
      myList.add(type);
      mySet.add(type);
    }
  }
}
