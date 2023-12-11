// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.uncheckedWarnings;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableArrayTypeFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UncheckedWarningLocalInspection extends AbstractBaseJavaLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "UNCHECKED_WARNING";
  @NonNls private static final String ID = "unchecked";
  private static final Logger LOG = Logger.getInstance(UncheckedWarningLocalInspection.class);
  public boolean IGNORE_UNCHECKED_ASSIGNMENT;
  public boolean IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION;
  public boolean IGNORE_UNCHECKED_CALL;
  public boolean IGNORE_UNCHECKED_CAST;
  public boolean IGNORE_UNCHECKED_OVERRIDING;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("IGNORE_UNCHECKED_ASSIGNMENT", JavaBundle.message("unchecked.warning.inspection.settings.ignore.unchecked.assignment")),
      checkbox("IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION", JavaBundle.message("unchecked.warning.inspection.settings.ignore.unchecked.generics.array.creation.for.vararg.parameter")),
      checkbox("IGNORE_UNCHECKED_CALL", JavaBundle.message("unchecked.warning.inspection.settings.ignore.unchecked.call.as.member.of.raw.type")),
      checkbox("IGNORE_UNCHECKED_CAST", JavaBundle.message("unchecked.warning.inspection.settings.ignore.unchecked.cast")),
      checkbox("IGNORE_UNCHECKED_OVERRIDING", JavaBundle.message("unchecked.warning.inspection.settings.ignore.unchecked.overriding"))
    );
  }

  private static @NotNull LocalQuickFix @NotNull [] getChangeVariableTypeFixes(@NotNull PsiVariable parameter, @Nullable PsiType itemType) {
    if (itemType instanceof PsiMethodReferenceType) return LocalQuickFix.EMPTY_ARRAY;
    LOG.assertTrue(parameter.isValid());
    final List<LocalQuickFix> result = new ArrayList<>();
    if (itemType != null) {
      for (ChangeVariableTypeQuickFixProvider fixProvider : ChangeVariableTypeQuickFixProvider.EP_NAME.getExtensionList()) {
        for (IntentionAction action : fixProvider.getFixes(parameter, itemType)) {
          if (action instanceof LocalQuickFix) {
            result.add((LocalQuickFix)action);
          }
        }
      }
    }

    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @NonNls
  public String getID() {
    return ID;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (IGNORE_UNCHECKED_ASSIGNMENT ||
        IGNORE_UNCHECKED_CALL ||
        IGNORE_UNCHECKED_CAST ||
        IGNORE_UNCHECKED_OVERRIDING ||
        IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION) {
      super.writeSettings(node);
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(session.getFile());
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) return super.buildVisitor(holder, isOnTheFly, session);

    return new UncheckedWarningsVisitor(isOnTheFly, languageLevel){
      @Override
      protected void registerProblem(@NotNull @InspectionMessage String message,
                                     @Nullable PsiElement callExpression,
                                     @NotNull PsiElement psiElement,
                                     @NotNull LocalQuickFix @NotNull [] quickFixes) {
        final String rawExpression = isMethodCalledOnRawType(callExpression);
        if (rawExpression != null) {
          final String referenceName = ((PsiMethodCallExpression)callExpression).getMethodExpression().getReferenceName();
          message += JavaBundle.message("unchecked.warning.inspection.reason.expr.has.raw.type.so.result.erased", rawExpression, referenceName);
        }

        PsiElement element2Highlight = null;
        if (psiElement instanceof PsiNewExpression) {
          element2Highlight = ((PsiNewExpression)psiElement).getClassOrAnonymousClassReference();
        }
        else if (psiElement instanceof PsiMethodCallExpression) {
          element2Highlight = ((PsiMethodCallExpression)psiElement).getMethodExpression();
        }

        holder.registerProblem(ObjectUtils.notNull(element2Highlight, psiElement), message, quickFixes);
      }
    };
  }


  private static String isMethodCalledOnRawType(PsiElement expression) {
    if (expression instanceof PsiMethodCallExpression) {
      final PsiExpression qualifierExpression = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiClass qualifierClass = PsiUtil.resolveClassInClassTypeOnly(qualifierExpression.getType());
        if (qualifierClass != null) {
          if (PsiUtil.isRawSubstitutor(qualifierClass, ((PsiMethodCallExpression)expression).resolveMethodGenerics().getSubstitutor())) {
            return qualifierExpression.getText();
          }
        }
      }
    }
    return null;
  }

  private abstract class UncheckedWarningsVisitor extends JavaElementVisitor {
    private final boolean myOnTheFly;
    @NotNull private final LanguageLevel myLanguageLevel;

    UncheckedWarningsVisitor(boolean onTheFly, @NotNull LanguageLevel level) {
      myOnTheFly = onTheFly;
      myLanguageLevel = level;
    }

    protected abstract void registerProblem(@NotNull @InspectionMessage String message,
                                            PsiElement callExpression,
                                            @NotNull PsiElement psiElement,
                                            @NotNull LocalQuickFix @NotNull [] quickFixes);


    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION) return;
      final JavaResolveResult result = expression.advancedResolve(false);
      if (JavaGenericsUtil.isUncheckedWarning(expression, result, myLanguageLevel)) {
        registerProblem(JavaBundle.message("unchecked.warning.inspection.message.unchecked.generics.array.creation.for.varargs.parameter"), null, expression, LocalQuickFix.EMPTY_ARRAY);
      }
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION) return;
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      if (classReference != null && JavaGenericsUtil.isUncheckedWarning(classReference, expression.resolveMethodGenerics(), myLanguageLevel)) {
        registerProblem(JavaBundle.message("unchecked.warning.inspection.message.unchecked.generics.array.creation.for.varargs.parameter"), expression, classReference, LocalQuickFix.EMPTY_ARRAY);
      }
    }

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      if (IGNORE_UNCHECKED_CAST) return;
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement == null) return;
      final PsiType castType = typeElement.getType();
      final PsiExpression operand = expression.getOperand();
      if (operand == null) return;
      final PsiType exprType = operand.getType();
      if (exprType == null) return;
      if (!TypeConversionUtil.areTypesConvertible(exprType, castType)) return;
      if (JavaGenericsUtil.isUncheckedCast(castType, exprType)) {
        final String description =
          JavaErrorBundle.message("generics.unchecked.cast", JavaHighlightUtil.formatType(exprType), JavaHighlightUtil
            .formatType(castType));
        registerProblem(description, operand, expression, LocalQuickFix.EMPTY_ARRAY);
      }
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (IGNORE_UNCHECKED_CALL) return;
      final JavaResolveResult result = expression.advancedResolve(false);
      final String description = getUncheckedCallDescription(expression, result);
      if (description != null) {
        final PsiElement referenceNameElement = expression.getReferenceNameElement();
        registerProblem(description, expression, referenceNameElement != null ? referenceNameElement : expression,
                        LocalQuickFix.EMPTY_ARRAY);
      }
    }

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      final JavaResolveResult result = callExpression.resolveMethodGenerics();
      final String description = getUncheckedCallDescription(callExpression, result);
      if (description != null) {
        if (IGNORE_UNCHECKED_CALL) return;
        registerProblem(description, null, callExpression, LocalQuickFix.EMPTY_ARRAY);
      }
      else {
        if (IGNORE_UNCHECKED_ASSIGNMENT) return;
        final PsiSubstitutor substitutor = result.getSubstitutor();
        final PsiExpressionList argumentList = callExpression.getArgumentList();
        if (argumentList != null) {
          final PsiMethod method = (PsiMethod)result.getElement();
          if (method != null) {
            final PsiExpression[] expressions = argumentList.getExpressions();
            final PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 0) {
              for (int i = 0; i < expressions.length; i++) {
                PsiParameter parameter = parameters[Math.min(i, parameters.length - 1)];
                final PsiExpression expression = expressions[i];
                final PsiType parameterType = substitutor.substitute(parameter.getType());
                final PsiType expressionType = expression.getType();
                if (expressionType != null) {
                  checkRawToGenericsAssignment(expression, expression, parameterType, expressionType, () -> LocalQuickFix.EMPTY_ARRAY);
                }
              }
            }
          }
        }
      }
    }

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null || initializer instanceof PsiArrayInitializerExpression) return;
      final PsiType initializerType = initializer.getType();
      checkRawToGenericsAssignment(initializer, initializer, variable.getType(), initializerType,
                                   () -> myOnTheFly ? getChangeVariableTypeFixes(variable, initializerType) : LocalQuickFix.EMPTY_ARRAY);
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      final PsiParameter parameter = statement.getIterationParameter();
      final PsiType parameterType = parameter.getType();
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) return;
      final PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
      checkRawToGenericsAssignment(parameter, iteratedValue, parameterType, itemType,
                                   () -> myOnTheFly ? getChangeVariableTypeFixes(parameter, itemType) : LocalQuickFix.EMPTY_ARRAY);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      if (!"=".equals(expression.getOperationSign().getText())) return;
      PsiExpression lExpr = expression.getLExpression();
      PsiExpression rExpr = expression.getRExpression();
      if (rExpr == null) return;
      PsiType lType = lExpr.getType();
      PsiType rType = rExpr.getType();
      if (rType == null) return;

      checkRawToGenericsAssignment(rExpr, rExpr, lType, rType, () -> {
        if (myOnTheFly && lExpr instanceof PsiReferenceExpression) {
          PsiElement element = ((PsiReferenceExpression)lExpr).resolve();
          if (element instanceof PsiVariable) {
            return getChangeVariableTypeFixes((PsiVariable)element, rType);
          }
        }

        return LocalQuickFix.EMPTY_ARRAY;
      });
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      if (PsiUtil.isLanguageLevel8OrHigher(expression) && PsiPolyExpressionUtil.isPolyExpression(expression)) {
        PsiType targetType = expression.getType();
        if (targetType == null) return;
        processConditionalPart(targetType, expression.getThenExpression());
        processConditionalPart(targetType, expression.getElseExpression());
      }
    }

    private void processConditionalPart(PsiType targetType, PsiExpression thenExpression) {
      if (thenExpression != null) {
        PsiType thenType = thenExpression.getType();
        if (thenType != null) {
          checkRawToGenericsAssignment(thenExpression, thenExpression, targetType, thenType, 
                                       () -> LocalQuickFix.EMPTY_ARRAY);
        }
      }
    }

    @Override
    public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression arrayInitializer) {
      super.visitArrayInitializerExpression(arrayInitializer);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      if (!(arrayInitializer.getType() instanceof PsiArrayType arrayType)) return;
      final PsiType componentType = arrayType.getComponentType();

      boolean arrayTypeFixChecked = false;
      VariableArrayTypeFix fix = null;

      final PsiExpression[] initializers = arrayInitializer.getInitializers();
      for (PsiExpression expression : initializers) {
        final PsiType itemType = expression.getType();

        if (itemType == null) continue;
        if (!TypeConversionUtil.isAssignable(componentType, itemType)) continue;
        if (JavaGenericsUtil.isRawToGeneric(componentType, itemType)) {
          String description = JavaErrorBundle.message("generics.unchecked.assignment",
                                                       itemType.getCanonicalText(),
                                                       componentType.getCanonicalText());
          if (!arrayTypeFixChecked) {
            final PsiType checkResult = JavaHighlightUtil.sameType(initializers);
            fix = checkResult != null ? VariableArrayTypeFix.createFix(arrayInitializer, checkResult) : null;
            arrayTypeFixChecked = true;
          }

          if (fix != null) {
            registerProblem(description, null, expression, new LocalQuickFix[]{LocalQuickFix.from(fix)});
          }
        }
      }
    }

    private void checkRawToGenericsAssignment(@NotNull PsiElement parameter,
                                              PsiExpression expression, PsiType parameterType,
                                              PsiType itemType,
                                              @NotNull Supplier<? extends @NotNull LocalQuickFix @NotNull []> fixesSupplier) {
      if (GenericsHighlightUtil.checkGenericArrayCreation(expression, expression.getType()) != null) return;
      if (parameterType == null || itemType == null) return;
      if (!TypeConversionUtil.isAssignable(parameterType, itemType)) return;
      if (JavaGenericsUtil.isRawToGeneric(parameterType, itemType)) {
        String description = JavaErrorBundle.message("generics.unchecked.assignment",
                                                     itemType.getCanonicalText(),
                                                     parameterType.getCanonicalText());
        registerProblem(description, expression, parameter, fixesSupplier.get());
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (IGNORE_UNCHECKED_OVERRIDING) return;
      if (!method.isConstructor()) {
        List<HierarchicalMethodSignature> superMethodSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
        if (!superMethodSignatures.isEmpty() && !method.hasModifierProperty(PsiModifier.STATIC)) {
          final MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
          for (MethodSignatureBackedByPsiMethod superSignature : superMethodSignatures) {
            PsiMethod baseMethod = superSignature.getMethod();
            PsiSubstitutor substitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(signature, superSignature);
            if (substitutor == null) substitutor = superSignature.getSubstitutor();
            if (PsiUtil.isRawSubstitutor(baseMethod, superSignature.getSubstitutor())) continue;
            final PsiType baseReturnType = substitutor.substitute(baseMethod.getReturnType());
            final PsiType overriderReturnType = method.getReturnType();
            if (baseReturnType == null || overriderReturnType == null) return;
            if (JavaGenericsUtil.isRawToGeneric(baseReturnType, overriderReturnType)) {
              final String message = JavaErrorBundle.message("unchecked.overriding.incompatible.return.type",
                                                             JavaHighlightUtil.formatType(overriderReturnType),
                                                             JavaHighlightUtil.formatType(baseReturnType));

              final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
              LOG.assertTrue(returnTypeElement != null);
              registerProblem(message, null, returnTypeElement, LocalQuickFix.EMPTY_ARRAY);
            }
          }
        }
      }
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      final PsiType returnType = PsiTypesUtil.getMethodReturnType(statement);
      if (returnType != null && !PsiTypes.voidType().equals(returnType)) {
        final PsiExpression returnValue = statement.getReturnValue();
        if (returnValue != null) {
          final PsiType valueType = returnValue.getType();
          if (valueType != null) {
            checkRawToGenericsAssignment(returnValue, returnValue, returnType, valueType, () -> {
              final PsiElement psiElement = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
              return psiElement instanceof PsiMethod
                     ? new LocalQuickFix[]{QuickFixFactory.getInstance().createMethodReturnFix((PsiMethod)psiElement, valueType, true)}
                     : LocalQuickFix.EMPTY_ARRAY;
            });
          }
        }
      }
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
      super.visitLambdaExpression(expression);

      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      PsiElement body = expression.getBody();
      if (body instanceof PsiExpression) {
        PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(expression);
        if (interfaceReturnType != null && !PsiTypes.voidType().equals(interfaceReturnType)) {
          PsiType type = ((PsiExpression)body).getType();
          if (type != null) {
            checkRawToGenericsAssignment(body, (PsiExpression)body, interfaceReturnType, type, () -> LocalQuickFix.EMPTY_ARRAY);
          }
        }
      }
    }

    @Nullable
    private static @InspectionMessage String getUncheckedCallDescription(PsiElement place, JavaResolveResult resolveResult) {
      final PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiMethod method)) return null;
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      if (!PsiUtil.isRawSubstitutor(method, substitutor)) {
        if (JavaVersionService.getInstance().isAtLeast(place, JavaSdkVersion.JDK_1_8)) {
          for (PsiTypeParameter parameter : method.getTypeParameters()) {
            final PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
            if (extendsListTypes.length > 0) {
              final PsiType subst = substitutor.substitute(parameter);
              for (PsiClassType classType : extendsListTypes) {
                if (JavaGenericsUtil.isRawToGeneric(substitutor.substitute(classType), subst)) {
                  return JavaErrorBundle.message("generics.unchecked.call", JavaHighlightUtil.formatMethod(method));
                }
              }
            }
          }
        }
        return null;
      }
      if (PsiTypesUtil.isUncheckedCall(resolveResult)) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
        PsiType type = elementFactory.createType(method.getContainingClass(), substitutor);
        return JavaErrorBundle.message("generics.unchecked.call.to.member.of.raw.type",
                                       JavaHighlightUtil.formatMethod(method),
                                       JavaHighlightUtil.formatType(type));
      }
      return null;
    }
  }
}
