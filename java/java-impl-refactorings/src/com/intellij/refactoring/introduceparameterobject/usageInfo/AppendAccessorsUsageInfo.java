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

package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.JavaBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class AppendAccessorsUsageInfo extends FixableUsageInfo{
  private final PsiClass myExistingClass;
  private final boolean myGenerateAccessors;
  private final ParameterInfoImpl myParameter;
  private final boolean myGetter;
  private final PsiField myField;

  public AppendAccessorsUsageInfo(PsiParameter psiParameter,
                                  PsiClass existingClass,
                                  boolean generateAccessors,
                                  ParameterInfoImpl parameter,
                                  boolean isGetter,
                                  PsiField field) {
    super(psiParameter);
    myExistingClass = existingClass;
    myGenerateAccessors = generateAccessors;
    myParameter = parameter;
    myGetter = isGetter;
    myField = field;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    if (myGenerateAccessors && myField != null) {
      myExistingClass.add(myGetter
                          ? GenerateMembersUtil.generateGetterPrototype(myField)
                          : GenerateMembersUtil.generateSetterPrototype(myField));
    }
  }

  public boolean isGetter() {
    return myGetter;
  }

  public ParameterInfoImpl getParameter() {
    return myParameter;
  }

  @Override
  public String getConflictMessage() {
    if (!myGenerateAccessors) {
      String fieldName = myParameter.getName();
      if (myField != null) {
        fieldName = myField.getName();
      }
      return JavaBundle.message("introduce.parameter.object.no.accessor.conflict.message", myGetter ? 0 : 1, fieldName);
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && ((AppendAccessorsUsageInfo)o).isGetter() == isGetter();
  }

  @Override
  public int hashCode() {
    return super.hashCode() * 29 + (isGetter() ? 1 : 0);
  }
}