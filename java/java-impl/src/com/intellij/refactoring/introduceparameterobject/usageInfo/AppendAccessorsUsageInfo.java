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

/*
 * User: anna
 * Date: 02-Nov-2009
 */
package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.introduceparameterobject.ParameterChunk;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

public class AppendAccessorsUsageInfo extends FixableUsageInfo{
  private final PsiClass myExistingClass;
  private final boolean myGenerateAccessors;
  private final PsiParameter myParameter;
  private final boolean myGetter;
  private final List<ParameterChunk> parameters;
  private static final Logger LOGGER = Logger.getInstance("#" + AppendAccessorsUsageInfo.class.getName());

  public AppendAccessorsUsageInfo(PsiClass existingClass,
                                  boolean generateAccessors,
                                  PsiParameter parameter,
                                  boolean isGetter,
                                  List<ParameterChunk> parameters) {
    super(parameter);
    myExistingClass = existingClass;
    myGenerateAccessors = generateAccessors;
    myParameter = parameter;
    myGetter = isGetter;
    this.parameters = parameters;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    if (myGenerateAccessors) {
      if (myExistingClass != null) {
        final ParameterChunk parameterChunk = ParameterChunk.getChunkByParameter(myParameter, parameters);
        LOGGER.assertTrue(parameterChunk != null);
        final PsiField field = parameterChunk.getField();
        if (field != null) {
          myExistingClass.add(myGetter
                              ? GenerateMembersUtil.generateGetterPrototype(field)
                              : GenerateMembersUtil.generateSetterPrototype(field));
        }
      }
    }
  }

  public boolean isGetter() {
    return myGetter;
  }

  public PsiParameter getParameter() {
    return myParameter;
  }

  @Override
  public String getConflictMessage() {
    if (!myGenerateAccessors) {
      String fieldName = myParameter.getName();
      final ParameterChunk chunk = ParameterChunk.getChunkByParameter(myParameter, parameters);
      if (chunk != null) {
        final PsiField field = chunk.getField();
        if (field != null) {
          fieldName = field.getName();
        }
      }
      return (myGetter ? "Getter" : "Setter") + " for field \'" + fieldName + "\' is required";
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