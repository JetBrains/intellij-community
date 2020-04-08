// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.compiler;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.SuppressByJavaCommentFix;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class JavacQuirksInspectionVisitor extends JavaElementVisitor {
  private static final ElementPattern<PsiElement> QUALIFIER_REFERENCE =
    psiElement().withParent(PsiJavaCodeReferenceElement.class).withSuperParent(2, PsiJavaCodeReferenceElement.class);

  private final ProblemsHolder myHolder;
  private final LanguageLevel myLanguageLevel;

  public JavacQuirksInspectionVisitor(ProblemsHolder holder) {
    myHolder = holder;
    myLanguageLevel = PsiUtil.getLanguageLevel(myHolder.getFile());
  }

  @Override
  public void visitAnnotationArrayInitializer(final PsiArrayInitializerMemberValue initializer) {
    if (PsiUtil.isLanguageLevel7OrHigher(initializer)) return;
    final PsiElement lastElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(initializer.getLastChild());
    if (lastElement != null && PsiUtil.isJavaToken(lastElement, JavaTokenType.COMMA)) {
      final String message = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.anno.array.comma.problem");
      final String fixName = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.anno.array.comma.fix");
      myHolder.registerProblem(lastElement, message, QuickFixFactory.getInstance().createDeleteFix(lastElement, fixName));
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
            final String message = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.qualifier.type.args.problem");
            final String fixName = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.qualifier.type.args.fix");
            myHolder.registerProblem(list, message, QuickFixFactory.getInstance().createDeleteFix(list, fixName));
          }
        }
      });
    }
  }

  @Override
  public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
    super.visitAssignmentExpression(assignment);
    final PsiType lType = assignment.getLExpression().getType();
    if (lType == null) return;
    final PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return;
    PsiJavaToken operationSign = assignment.getOperationSign();
    checkIntersectionType(lType, rExpression.getType(), operationSign);

    IElementType eqOpSign = operationSign.getTokenType();
    IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
    if (opSign == null) return;

    if (JavaSdkVersion.JDK_1_6.equals(JavaVersionService.getInstance().getJavaSdkVersion(assignment)) &&
        PsiType.getJavaLangObject(assignment.getManager(), assignment.getResolveScope()).equals(lType)) {
      String operatorText = operationSign.getText().substring(0, operationSign.getText().length() - 1);
      String message = JavaErrorBundle.message("binary.operator.not.applicable", operatorText,
                                               JavaHighlightUtil.formatType(lType),
                                               JavaHighlightUtil.formatType(rExpression.getType()));

      myHolder.registerProblem(assignment, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new ReplaceAssignmentOperatorWithAssignmentFix(operationSign.getText()));
    }
  }

  @Override
  public void visitVariable(PsiVariable variable) {
    super.visitVariable(variable);
    final PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      final PsiElement assignmentToken = PsiTreeUtil.skipWhitespacesBackward(initializer);
      if (assignmentToken != null) {
        checkIntersectionType(variable.getType(), initializer.getType(), assignmentToken);
      }
    }
  }

  private void checkIntersectionType(@NotNull PsiType lType, @Nullable PsiType rType, @NotNull PsiElement elementToHighlight) {
    if (rType instanceof PsiIntersectionType && TypeConversionUtil.isAssignable(lType, rType)) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(lType);
      if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.FINAL)) {
        final PsiType[] conjuncts = ((PsiIntersectionType)rType).getConjuncts();
        for (PsiType conjunct : conjuncts) {
          if (!TypeConversionUtil.isAssignable(conjunct, lType)) {
            final String descriptionTemplate =
              "Though assignment is formal correct, it could lead to ClassCastException at runtime. Expected: '" + lType.getPresentableText() + "', actual: '" + rType.getPresentableText() + "'";
            myHolder.registerProblem(elementToHighlight, descriptionTemplate);
          }
        }
      }
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    super.visitMethodCallExpression(expression);
    if (PsiUtil.isLanguageLevel8OrHigher(expression) && expression.getTypeArguments().length == 0) {
      PsiExpression[] args = expression.getArgumentList().getExpressions();
      JavaResolveResult resolveResult = expression.resolveMethodGenerics();
      if (resolveResult instanceof MethodCandidateInfo) {
        PsiMethod method = ((MethodCandidateInfo)resolveResult).getElement();
        if (method.isVarArgs() && method.hasTypeParameters() && args.length > method.getParameterList().getParametersCount() + 50) {
          PsiSubstitutor substitutor = resolveResult.getSubstitutor();
          for (PsiTypeParameter typeParameter : method.getTypeParameters()) {
            if (!PsiTypesUtil.isDenotableType(substitutor.substitute(typeParameter), expression)) {
              return;
            }
          }

          int count = 0;
          for (int i = method.getParameterList().getParametersCount(); i < args.length; i++) {
            if (PsiPolyExpressionUtil.isPolyExpression(args[i]) && ++ count > 50) {
              myHolder.registerProblem(expression.getMethodExpression(),
                                       JavaAnalysisBundle
                                         .message("vararg.method.call.with.50.poly.arguments"),
                                       new MyAddExplicitTypeArgumentsFix());
              break;
            }
          }
        }
      }
    }
  }

  @Override
  public void visitBinaryExpression(PsiBinaryExpression expression) {
    super.visitBinaryExpression(expression);
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_7) && !myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      PsiType ltype = expression.getLOperand().getType();
      PsiExpression rOperand = expression.getROperand();
      if (rOperand != null) {
        PsiType rtype = rOperand.getType();
        if (ltype != null && rtype != null &&
            (ltype.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ^ rtype.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) &&
            (TypeConversionUtil.isPrimitiveAndNotNull(ltype) ^ TypeConversionUtil.isPrimitiveAndNotNull(rtype)) &&
            TypeConversionUtil.isBinaryOperatorApplicable(expression.getOperationTokenType(), ltype, rtype, false) &&
            TypeConversionUtil.areTypesConvertible(rtype, ltype)) {
          myHolder.registerProblem(expression.getOperationSign(), JavaAnalysisBundle
            .message("comparision.between.object.and.primitive"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    }
  }

  private static class ReplaceAssignmentOperatorWithAssignmentFix implements LocalQuickFix {
    private final String myOperationSign;

    ReplaceAssignmentOperatorWithAssignmentFix(String operationSign) {
      myOperationSign = operationSign;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaAnalysisBundle.message("replace.0.with", myOperationSign);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaAnalysisBundle.message("replace.operator.assignment.with.assignment");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiAssignmentExpression) {
        PsiReplacementUtil.replaceOperatorAssignmentWithAssignmentExpression((PsiAssignmentExpression)element);
      }
    }
  }

  private static class MyAddExplicitTypeArgumentsFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("add.type.arguments.single.argument.text");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiReferenceExpression) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          PsiExpression withArgs = AddTypeArgumentsFix.addTypeArguments((PsiExpression)parent, null);
          if (withArgs == null) return;
          element = WriteAction.compute(() -> CodeStyleManager.getInstance(project).reformat(parent.replace(withArgs)));
          new SuppressByJavaCommentFix(RedundantTypeArgsInspection.SHORT_NAME + " (explicit type arguments speedup compilation and analysis time)")
            .invoke(project, element);
        }
      }
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
