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

package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.util.IncorrectOperationException;

public class CopyDefaultConstructorUsageInfo extends FixableUsageInfo{
  private final PsiClass myTargetClass;
  private final PsiMethod myConstructor;


  public CopyDefaultConstructorUsageInfo(PsiClass targetClass, PsiMethod constructor) {
    super(constructor);
    myTargetClass = targetClass;
    myConstructor = constructor;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    final PsiCodeBlock body = myConstructor.getBody();
    assert body != null;
    if (body.getFirstBodyElement() != null) { //do not copy empty constructor
      final PsiElement constructorCopy = myConstructor.copy();
      final PsiClass srcClass = myConstructor.getContainingClass();
      assert srcClass != null;
      InlineUtil.substituteTypeParams(constructorCopy, TypeConversionUtil.getSuperClassSubstitutor(srcClass, myTargetClass, PsiSubstitutor.EMPTY), JavaPsiFacade.getElementFactory(getProject()));
      myTargetClass.add(constructorCopy);
    }
  }
}