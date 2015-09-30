/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.inspections;

import com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationVariableTypeFixProvider;
import com.intellij.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.BaseGuavaTypeConversionRule;
import com.intellij.refactoring.typeMigration.rules.guava.GuavaFunctionConversionRule;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
@SuppressWarnings("DialogTitleCapitalization")
public class GuavaInspection extends BaseJavaLocalInspectionTool {
  private final static String PROBLEM_DESCRIPTION = "Guava's functional primitives can be replaced by Java API";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      private final AtomicNotNullLazyValue<Map<String, PsiClass>> myGuavaClassConversions =
        new AtomicNotNullLazyValue<Map<String, PsiClass>>() {
          @NotNull
          @Override
          protected Map<String, PsiClass> compute() {
            Map<String, PsiClass> map = new HashMap<String, PsiClass>();
            for (TypeConversionRule rule : TypeConversionRule.EP_NAME.getExtensions()) {
              if (rule instanceof BaseGuavaTypeConversionRule) {
                final String fromClass = ((BaseGuavaTypeConversionRule)rule).ruleFromClass();
                final String toClass = ((BaseGuavaTypeConversionRule)rule).ruleToClass();

                final Project project = holder.getProject();
                final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
                final PsiClass targetClass = javaPsiFacade.findClass(toClass, GlobalSearchScope.allScope(project));

                if (targetClass != null) {
                  map.put(fromClass, targetClass);
                }

              }
            }
            return map;
          }
        };

      @Override
      public void visitVariable(PsiVariable variable) {
        final PsiType type = variable.getType();
        if (type instanceof PsiClassType) {
          final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
          final PsiClass psiClass = resolveResult.getElement();
          if (psiClass != null) {
            final String qName = psiClass.getQualifiedName();
            final PsiClass targetClass = myGuavaClassConversions.getValue().get(qName);
            if (targetClass != null) {
              final Collection<PsiType> typeParameters = resolveResult.getSubstitutor().getSubstitutionMap().values();
              final PsiClassType targetType =
                JavaPsiFacade.getElementFactory(holder.getProject()).createType(targetClass, typeParameters.toArray(new PsiType[typeParameters.size()]));
              final VariableTypeFix fix = TypeMigrationVariableTypeFixProvider.createTypeMigrationFix(variable, targetType);
              holder.registerProblem(variable, PROBLEM_DESCRIPTION, fix);
            }
          }
        }
      }
    };
  }
}