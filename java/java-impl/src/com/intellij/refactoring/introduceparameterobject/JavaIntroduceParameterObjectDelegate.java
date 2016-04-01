/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaIntroduceParameterObjectDelegate {

  @Nullable
  static PsiMethod existingClassIsCompatible(PsiClass aClass, List<ParameterChunk> params) {
    if (params.size() == 1) {
      final ParameterChunk parameterChunk = params.get(0);
      final PsiType paramType = parameterChunk.getParameter().type;
      if (TypeConversionUtil.isPrimitiveWrapper(aClass.getQualifiedName())) {
        parameterChunk.setField(aClass.findFieldByName("value", false));
        parameterChunk.setGetter(paramType.getCanonicalText() + "Value");
        for (PsiMethod constructor : aClass.getConstructors()) {
          if (constructorIsCompatible(constructor, params)) return constructor;
        }
      }
    }
    final PsiMethod[] constructors = aClass.getConstructors();
    PsiMethod compatibleConstructor = null;
    for (PsiMethod constructor : constructors) {
      if (constructorIsCompatible(constructor, params)) {
        compatibleConstructor = constructor;
        break;
      }
    }
    if (compatibleConstructor == null) {
      return null;
    }
    final PsiParameterList parameterList = compatibleConstructor.getParameterList();
    final PsiParameter[] constructorParams = parameterList.getParameters();
    for (int i = 0; i < constructorParams.length; i++) {
      final PsiParameter param = constructorParams[i];
      final ParameterChunk parameterChunk = params.get(i);

      final PsiField field = findFieldAssigned(param, compatibleConstructor);
      if (field == null) {
        return null;
      }

      parameterChunk.setField(field);

      final PsiMethod getterForField = PropertyUtil.findGetterForField(field);
      if (getterForField != null) {
        parameterChunk.setGetter(getterForField.getName());
      }

      final PsiMethod setterForField = PropertyUtil.findSetterForField(field);
      if (setterForField != null) {
        parameterChunk.setSetter(setterForField.getName());
      }
    }
    return compatibleConstructor;
  }

  private static boolean constructorIsCompatible(PsiMethod constructor, List<ParameterChunk> params) {
    final PsiParameterList parameterList = constructor.getParameterList();
    final PsiParameter[] constructorParams = parameterList.getParameters();
    if (constructorParams.length != params.size()) {
      return false;
    }
    for (int i = 0; i < constructorParams.length; i++) {
      if (!TypeConversionUtil.isAssignable(constructorParams[i].getType(), params.get(i).getParameter().type)) {
        return false;
      }
    }
    return true;
  }

  private static PsiField findFieldAssigned(PsiParameter param, PsiMethod constructor) {
    final ParamAssignmentFinder visitor = new ParamAssignmentFinder(param);
    constructor.accept(visitor);
    return visitor.getFieldAssigned();
  }

  private static class ParamAssignmentFinder extends JavaRecursiveElementWalkingVisitor {

    private final PsiParameter param;

    private PsiField fieldAssigned = null;

    ParamAssignmentFinder(PsiParameter param) {
      this.param = param;
    }

    public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final PsiExpression lhs = assignment.getLExpression();
      final PsiExpression rhs = assignment.getRExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference)rhs).resolve();
      if (referent == null || !referent.equals(param)) {
        return;
      }
      final PsiElement assigned = ((PsiReference)lhs).resolve();
      if (assigned == null || !(assigned instanceof PsiField)) {
        return;
      }
      fieldAssigned = (PsiField)assigned;
    }

    public PsiField getFieldAssigned() {
      return fieldAssigned;
    }
  }
}
