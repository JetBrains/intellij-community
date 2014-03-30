/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.compiler;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class JavacQuirksInspectionVisitor extends JavaElementVisitor {
  private static final ElementPattern QUALIFIER_REFERENCE =
    psiElement().withParent(PsiJavaCodeReferenceElement.class).withSuperParent(2, PsiJavaCodeReferenceElement.class);

  private final ProblemsHolder myHolder;

  public JavacQuirksInspectionVisitor(ProblemsHolder holder) {
    myHolder = holder;
  }

  @Override
  public void visitAnnotationArrayInitializer(final PsiArrayInitializerMemberValue initializer) {
    if (PsiUtil.isLanguageLevel7OrHigher(initializer)) return;
    final PsiElement lastElement = PsiTreeUtil.skipSiblingsBackward(initializer.getLastChild(), PsiWhiteSpace.class, PsiComment.class);
    if (lastElement != null && PsiUtil.isJavaToken(lastElement, JavaTokenType.COMMA)) {
      final String message = InspectionsBundle.message("inspection.compiler.javac.quirks.anno.array.comma.problem");
      final String fixName = InspectionsBundle.message("inspection.compiler.javac.quirks.anno.array.comma.fix");
      myHolder.registerProblem(lastElement, message, new RemoveElementQuickFix(fixName));
    }
  }

  @Override
  public void visitTypeCastExpression(final PsiTypeCastExpression expression) {
    if (PsiUtil.isLanguageLevel7OrHigher(expression)) return;
    final PsiTypeElement type = expression.getCastType();
    if (type != null) {
      type.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceParameterList(final PsiReferenceParameterList list) {
          super.visitReferenceParameterList(list);
          if (list.getFirstChild() != null && QUALIFIER_REFERENCE.accepts(list)) {
            final String message = InspectionsBundle.message("inspection.compiler.javac.quirks.qualifier.type.args.problem");
            final String fixName = InspectionsBundle.message("inspection.compiler.javac.quirks.qualifier.type.args.fix");
            myHolder.registerProblem(list, message, new RemoveElementQuickFix(fixName));
          }
        }
      });
    }
  }
}
