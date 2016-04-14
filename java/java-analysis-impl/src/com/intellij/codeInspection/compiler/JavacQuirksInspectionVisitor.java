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
package com.intellij.codeInspection.compiler;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class JavacQuirksInspectionVisitor extends JavaElementVisitor {
  private static final ElementPattern QUALIFIER_REFERENCE =
    psiElement().withParent(PsiJavaCodeReferenceElement.class).withSuperParent(2, PsiJavaCodeReferenceElement.class);

  private final ProblemsHolder myHolder;
  private final LanguageLevel myLanguageLevel;
  private final JavaSdkVersion mySdkVersion;

  public JavacQuirksInspectionVisitor(ProblemsHolder holder) {
    myHolder = holder;
    mySdkVersion = JavaVersionService.getInstance().getJavaSdkVersion(myHolder.getFile());
    myLanguageLevel = PsiUtil.getLanguageLevel(myHolder.getFile());
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

  @Override
  public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
    super.visitAssignmentExpression(assignment);
    final PsiType lType = assignment.getLExpression().getType();
    PsiJavaToken operationSign = assignment.getOperationSign();
    IElementType eqOpSign = operationSign.getTokenType();
    IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
    if (opSign == null) return;
    final PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return;
    if (JavaSdkVersion.JDK_1_6.equals(JavaVersionService.getInstance().getJavaSdkVersion(assignment)) &&
        PsiType.getJavaLangObject(assignment.getManager(), assignment.getResolveScope()).equals(lType)) {
      String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
      String message = JavaErrorMessages.message("binary.operator.not.applicable", operatorText,
                                                 JavaHighlightUtil.formatType(lType),
                                                 JavaHighlightUtil.formatType(rExpression.getType()));

      myHolder.registerProblem(assignment, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new ReplaceAssignmentOperatorWithAssignmentFix(operationSign.getText()));
    }
  }

  @Override
  public void visitIdentifier(PsiIdentifier identifier) {
    super.visitIdentifier(identifier);
    if ("_".equals(identifier.getText()) &&
        mySdkVersion != null && mySdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8) &&
        myLanguageLevel.isLessThan(LanguageLevel.JDK_1_9)) {
      final String message = JavaErrorMessages.message("underscore.identifier.warn");
      myHolder.registerProblem(identifier, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
  }

  private static class ReplaceAssignmentOperatorWithAssignmentFix implements LocalQuickFix {
    private final String myOperationSign;

    public ReplaceAssignmentOperatorWithAssignmentFix(String operationSign) {
      myOperationSign = operationSign;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace ''" + myOperationSign + "'' with ''=''";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace Operator Assignment with Assignment";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiAssignmentExpression) {
        PsiReplacementUtil.replaceOperatorAssignmentWithAssignmentExpression((PsiAssignmentExpression)element);
      }
    }
  }
}
