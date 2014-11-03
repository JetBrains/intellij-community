/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FunctionalInterfaceSuggester {
  public static Collection<? extends PsiType> suggestFunctionalInterfaces(final @NotNull PsiFunctionalExpression expression) {

    final Project project = expression.getProject();
    final PsiClass functionalInterfaceClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, GlobalSearchScope.allScope(project));
    if (functionalInterfaceClass == null) {
      return Collections.emptyList();
    }
    final Set<PsiType> types = new LinkedHashSet<PsiType>();
    final String uniqueExprName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("l", expression, true);
    AnnotatedMembersSearch.search(functionalInterfaceClass, expression.getResolveScope()).forEach(new Processor<PsiMember>() {
      @Override
      public boolean process(PsiMember member) {
        if (member instanceof PsiClass) {
          final PsiType type = getAcceptableType((PsiClass)member, expression, uniqueExprName);
          if (type != null) {
            types.add(type);
          }
        }
        return true;
      }
    });
    return types;
  }

  private static PsiType getAcceptableType(PsiClass interface2Consider, PsiFunctionalExpression expression, String uniqueExprName) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(interface2Consider.getProject());
    //todo try to infer type
    final PsiDeclarationStatement exprDeclaration = (PsiDeclarationStatement)elementFactory
      .createStatementFromText(interface2Consider.getQualifiedName() + " " + uniqueExprName + " = " + expression.getText() + ";", expression);

    final PsiLocalVariable var = (PsiLocalVariable)exprDeclaration.getDeclaredElements()[0];
    final PsiExpression exprAsInitializer = var.getInitializer();
    if (exprAsInitializer instanceof PsiFunctionalExpression) {

      if (!((PsiFunctionalExpression)exprAsInitializer).isAcceptable(var.getType())) {
        return null;
      }
      final PsiType type = ((PsiFunctionalExpression)exprAsInitializer).getFunctionalInterfaceType();
      if (type instanceof PsiLambdaExpressionType || type instanceof PsiLambdaParameterType || type instanceof PsiMethodReferenceType) {
        return null;
      }
      return type;
    }

    return null;
  }
}
