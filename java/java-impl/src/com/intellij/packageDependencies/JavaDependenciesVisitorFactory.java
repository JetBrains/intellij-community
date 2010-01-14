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
 * Date: 21-Jan-2008
 */
package com.intellij.packageDependencies;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;

public class JavaDependenciesVisitorFactory extends DependenciesVisitorFactory {
  @Override
  public PsiElementVisitor createVisitor(final DependenciesBuilder.DependencyProcessor processor) {
    return new JavaRecursiveElementVisitor() {

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitReferenceElement(expression);
      }

      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          PsiElement resolved = ref.resolve();
          if (resolved != null) {
            processor.process(ref.getElement(), resolved);
          }
        }
      }

      @Override
      public void visitLiteralExpression(PsiLiteralExpression expression) {
        // empty
        // TODO: thus we'll skip property references and references to file resources. We can't validate them anyway now since
        // TODO: rule syntax does not allow this.
      }

      @Override
      public void visitDocComment(PsiDocComment comment) {
        //empty
      }

      @Override
      public void visitImportStatement(PsiImportStatement statement) {
        if (!DependencyValidationManager.getInstance(statement.getProject()).skipImportStatements()) {
          visitElement(statement);
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiMethod psiMethod = expression.resolveMethod();
        if (psiMethod != null) {
          PsiType returnType = psiMethod.getReturnType();
          if (returnType != null) {
            PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
            if (psiClass != null) {
              processor.process(expression, psiClass);
            }
          }
        }
      }
    };
  }
}