// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.compiler;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeArgumentsFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

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
  public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression methodRef) {
    PsiMethod method = ObjectUtils.tryCast(methodRef.resolve(), PsiMethod.class);
    PsiClass targetClass = getInaccessibleMethodReferenceClass(methodRef, method);
    if (targetClass == null) return;
    String className = PsiFormatUtil.formatClass(targetClass, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_FQ_NAME);
    myHolder.registerProblem(methodRef,
                             JavaAnalysisBundle.message("inspection.quirk.method.reference.return.type.message", className));
  }

  /**
   * @param context PsiElement where accessibility should be checked
   * @param method method reference target method
   * @return class that needs to be accessible at runtime to link the method reference but is not accessible at runtime;
   * null if there's no accessibility problem
   */
  public static @Nullable PsiClass getInaccessibleMethodReferenceClass(@NotNull PsiElement context, @Nullable PsiMethod method) {
    if (method == null) return null;
    PsiClass targetClass = PsiUtil.resolveClassInType(TypeConversionUtil.erasure(method.getReturnType()));
    if (targetClass == null) return null;
    if (!targetClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !targetClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      return null;
    }
    if (JavaResolveUtil.isAccessible(targetClass, targetClass.getContainingClass(), targetClass.getModifierList(), context, null, null)) {
      return null;
    }
    return targetClass;
  }

  @Override
  public void visitAnnotationArrayInitializer(final @NotNull PsiArrayInitializerMemberValue initializer) {
    if (PsiUtil.isLanguageLevel7OrHigher(initializer)) return;
    final PsiElement lastElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(initializer.getLastChild());
    if (PsiUtil.isJavaToken(lastElement, JavaTokenType.COMMA)) {
      final String message = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.anno.array.comma.problem");
      final String fixName = JavaAnalysisBundle.message("inspection.compiler.javac.quirks.anno.array.comma.fix");
      myHolder.registerProblem(lastElement, message, QuickFixFactory.getInstance().createDeleteFix(lastElement, fixName));
    }
  }

  @Override
  public void visitTypeParameterList(@NotNull PsiTypeParameterList list) {
    if (PsiUtil.isLanguageLevel7OrHigher(list)) return;
    PsiTypeParameter[] parameters = list.getTypeParameters();
    for (int i = 0; i < parameters.length; i++) {
      PsiTypeParameter typeParameter = parameters[i];
      for (PsiJavaCodeReferenceElement referenceElement : typeParameter.getExtendsList().getReferenceElements()) {
        PsiElement resolve = referenceElement.resolve();
        if (resolve instanceof PsiTypeParameter && ArrayUtilRt.find(parameters, resolve) > i) {
          myHolder.registerProblem(referenceElement,
                                   JavaAnalysisBundle.message("inspection.compiler.javac.quirks.illegal.forward.reference"));
        }
      }
    }
  }

  @Override
  public void visitTypeCastExpression(final @NotNull PsiTypeCastExpression expression) {
    if (PsiUtil.isLanguageLevel7OrHigher(expression)) return;
    final PsiTypeElement type = expression.getCastType();
    if (type != null) {
      type.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceParameterList(final @NotNull PsiReferenceParameterList list) {
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
  public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
    super.visitAssignmentExpression(assignment);
    final PsiType lType = assignment.getLExpression().getType();
    if (lType == null) return;
    final PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return;
    PsiJavaToken operationSign = assignment.getOperationSign();

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
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    super.visitMethodCallExpression(expression);
    if (expression.getTypeArguments().length == 0) {
      PsiExpression[] args = expression.getArgumentList().getExpressions();
      JavaResolveResult resolveResult = expression.resolveMethodGenerics();
      if (resolveResult instanceof MethodCandidateInfo) {
        PsiMethod method = ((MethodCandidateInfo)resolveResult).getElement();
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        if (PsiUtil.isLanguageLevel8OrHigher(expression) &&
            method.isVarArgs() &&
            method.hasTypeParameters() &&
            args.length > method.getParameterList().getParametersCount() + 50) {
          for (PsiTypeParameter typeParameter : method.getTypeParameters()) {
            if (!PsiTypesUtil.isDenotableType(substitutor.substitute(typeParameter), expression)) {
              return;
            }
          }

          if (isSuspicious(args, method)) {
            myHolder.registerProblem(expression.getMethodExpression(),
                                     JavaAnalysisBundle.message("vararg.method.call.with.50.poly.arguments"),
                                     new MyAddExplicitTypeArgumentsFix());
          }
        }
        if (resolveResult.isValidResult()) {
          for (PsiType value : substitutor.getSubstitutionMap().values()) {
            if (value instanceof PsiIntersectionType) {
              PsiClass aClass = Arrays.stream(((PsiIntersectionType)value).getConjuncts())
                .map(PsiUtil::resolveClassInClassTypeOnly)
                .filter(_aClass -> _aClass != null && _aClass.hasModifierProperty(PsiModifier.FINAL))
                .findFirst().orElse(null);
              if (aClass != null && aClass.hasModifierProperty(PsiModifier.FINAL)) {
                for (PsiType conjunct : ((PsiIntersectionType)value).getConjuncts()) {
                  PsiClass currentClass = PsiUtil.resolveClassInClassTypeOnly(conjunct);
                  if (currentClass != null &&
                      !aClass.equals(currentClass) &&
                      !aClass.isInheritor(currentClass, true)) {
                    final String descriptionTemplate =
                      JavaAnalysisBundle.message("inspection.message.javac.quick.intersection.type.problem",
                                                 value.getPresentableText(), ObjectUtils.notNull(aClass.getQualifiedName(),
                                                                                                 Objects.requireNonNull(aClass.getName())));
                    myHolder.registerProblem(expression.getMethodExpression(), descriptionTemplate);
                  }
                }
                break;
              }
            }
          }
        }
      }
    }
  }

  public static boolean isSuspicious(PsiExpression[] args, PsiMethod method) {
    int count = 0;
    for (int i = method.getParameterList().getParametersCount(); i < args.length; i++) {
      if (PsiPolyExpressionUtil.isPolyExpression(args[i]) && ++count > 50) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
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

  @Override
  public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement ref) {
    if (myLanguageLevel.isAtLeast(LanguageLevel.JDK_1_9)) return;//javac 9 has no such bug
    if (ref.getParent() instanceof PsiTypeElement) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
      if (psiClass == null) return;
      if (PsiTreeUtil.isAncestor(psiClass.getExtendsList(), ref, false) ||
          PsiTreeUtil.isAncestor(psiClass.getImplementsList(), ref, false)) {
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).resolve() == psiClass) {
          final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getParentOfType(ref, PsiJavaCodeReferenceElement.class);
          if (referenceElement == null) return;
          final PsiElement typeClass = referenceElement.resolve();
          if (!(typeClass instanceof PsiClass)) return;
          final PsiElement resolve = ref.resolve();
          final PsiClass containingClass = resolve != null ? ((PsiClass)resolve).getContainingClass() : null;
          if (containingClass == null) return;
          PsiClass hiddenClass;
          if (psiClass.isInheritor(containingClass, true)) {
            hiddenClass = (PsiClass)resolve;
          }
          else {
            hiddenClass = unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance((PsiClass)typeClass, ((PsiClass)resolve).getExtendsList());
            if (hiddenClass == null) {
              hiddenClass = unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance((PsiClass)typeClass, ((PsiClass)resolve).getImplementsList());
            }
          }
          if (hiddenClass != null) {
            myHolder.registerProblem(ref, JavaErrorBundle.message("text.class.is.not.accessible", hiddenClass.getName()));
          }
        }
      }
    }
  }

  private static PsiClass unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance(@NotNull PsiClass containingClass,
                                                                                               @Nullable PsiReferenceList referenceList) {
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
        if (!referenceElement.isQualified()) {
          final PsiElement superClass = referenceElement.resolve();
          if (superClass instanceof PsiClass) {
            final PsiClass superContainingClass = ((PsiClass)superClass).getContainingClass();
            if (superContainingClass != null &&
                InheritanceUtil.isInheritorOrSelf(containingClass, superContainingClass, true) &&
                !PsiTreeUtil.isAncestor(superContainingClass, containingClass, true)) {
              return (PsiClass)superClass;
            }
          }
        }
      }
    }
    return null;
  }


  private static class ReplaceAssignmentOperatorWithAssignmentFix extends PsiUpdateModCommandQuickFix {
    private final String myOperationSign;

    ReplaceAssignmentOperatorWithAssignmentFix(String operationSign) {
      myOperationSign = operationSign;
    }

    @Override
    public @Nls @NotNull String getName() {
      return JavaAnalysisBundle.message("replace.0.with", myOperationSign);
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("replace.operator.assignment.with.assignment");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiAssignmentExpression assignment) {
        PsiReplacementUtil.replaceOperatorAssignmentWithAssignmentExpression(assignment);
      }
    }
  }

  private static class MyAddExplicitTypeArgumentsFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return QuickFixBundle.message("add.type.arguments.single.argument.text");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiReferenceExpression) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression call) {
          PsiExpression withArgs = AddTypeArgumentsFix.addTypeArguments(call, null);
          if (withArgs == null) return;
          parent.replace(withArgs);
        }
      }
    }
  }
}
