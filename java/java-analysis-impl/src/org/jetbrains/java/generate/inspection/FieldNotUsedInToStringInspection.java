/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.inspection;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.GenerateToStringContext;
import org.jetbrains.java.generate.GenerateToStringUtils;

import java.util.Collections;

/**
 * Inspection to check if the current class toString() method is out of
 * sync with the fields defined. It uses filter information from the settings
 * to exclude certain fields (eg. constants etc.). Will only warn if the
 * class has a toString() method.
 */
public class FieldNotUsedInToStringInspection extends AbstractToStringInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Field not used in 'toString()' method";
  }

  @Override
  @NotNull
  public String getShortName() {
    return "FieldNotUsedInToString";
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new FieldNotUsedInToStringVisitor(holder);
  }

  private static class FieldNotUsedInToStringVisitor extends JavaElementVisitor{

    private final ProblemsHolder myHolder;

    private FieldNotUsedInToStringVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      @NonNls final String methodName = method.getName();
      if (!"toString".equals(methodName)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      final PsiType returnType = method.getReturnType();
      final PsiClassType javaLangString = PsiType.getJavaLangString(method.getManager(), method.getResolveScope());
      if (!javaLangString.equals(returnType)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final PsiField[] fields =
        GenerateToStringUtils.filterAvailableFields(aClass, GenerateToStringContext.getConfig().getFilterPattern());
      final PsiMethod[] methods;
      if (GenerateToStringContext.getConfig().isEnableMethods()) {
        methods = GenerateToStringUtils.filterAvailableMethods(aClass, GenerateToStringContext.getConfig().getFilterPattern());
      }
      else {
        methods = PsiMethod.EMPTY_ARRAY;
      }
      final FieldUsedVisitor visitor = new FieldUsedVisitor(fields, methods);
      method.accept(visitor);
      for (PsiField field : visitor.getUnusedFields()) {
        final String fieldName = field.getName();
        myHolder.registerProblem(field.getNameIdentifier(), "Field '" + fieldName + "' is not used in 'toString()' method",
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, GenerateToStringQuickFix.getInstance());
      }
      for (PsiMethod unusedMethod : visitor.getUnusedMethods()) {
        final PsiIdentifier identifier = unusedMethod.getNameIdentifier();
        final PsiElement target = identifier == null ? unusedMethod : identifier;
        myHolder.registerProblem(target, "Method '" + unusedMethod.getName() + "' is not used in 'toString()' method",
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, GenerateToStringQuickFix.getInstance());
      }
    }
  }

  private static class FieldUsedVisitor extends JavaRecursiveElementWalkingVisitor {

    private final THashSet<PsiField> myUnusedFields = new THashSet<>();
    private final THashSet<PsiMethod> myUnusedMethods = new THashSet<>();

    FieldUsedVisitor(PsiField[] fields, PsiMethod[] methods) {
      Collections.addAll(myUnusedFields, fields);
      Collections.addAll(myUnusedMethods, methods);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (myUnusedFields.isEmpty() && myUnusedMethods.isEmpty()) {
        return;
      }
      super.visitReferenceExpression(expression);
      final PsiElement target = expression.resolve();
      if (target instanceof PsiField) {
        final PsiField field = (PsiField)target;
        myUnusedFields.remove(field);
      }
      else if (target instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)target;
        if (usesReflection(method)) {
          myUnusedFields.clear();
          myUnusedMethods.clear();
        }
        else {
          myUnusedMethods.remove(method);
          final PsiField field = PropertyUtilBase.findPropertyFieldByMember(method);
          myUnusedFields.remove(field);
        }
      }
    }

    private static boolean usesReflection(PsiMethod method) {
      @NonNls final String name = method.getName();
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      @NonNls final String qualifiedName = containingClass.getQualifiedName();
      if ("getDeclaredFields".equals(name)) {
        return "java.lang.Class".equals(qualifiedName);
      }
      if ("toString".equals(name)) {
        return "org.apache.commons.lang.builder.ReflectionToStringBuilder".equals(qualifiedName) ||
          "java.util.Objects".equals(qualifiedName);
      }
      return false;
    }

    THashSet<PsiField> getUnusedFields() {
      return myUnusedFields;
    }

    THashSet<PsiMethod> getUnusedMethods() {
      return myUnusedMethods;
    }
  }
}
