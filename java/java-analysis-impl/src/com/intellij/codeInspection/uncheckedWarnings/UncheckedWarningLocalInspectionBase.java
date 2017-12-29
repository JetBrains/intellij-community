// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.uncheckedWarnings;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableArrayTypeFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class UncheckedWarningLocalInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "UNCHECKED_WARNING";
  public static final String DISPLAY_NAME = InspectionsBundle.message("unchecked.warning");
  @NonNls private static final String ID = "unchecked";
  private static final Logger LOG = Logger.getInstance("#" + UncheckedWarningLocalInspectionBase.class);
  public boolean IGNORE_UNCHECKED_ASSIGNMENT;
  public boolean IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION;
  public boolean IGNORE_UNCHECKED_CALL;
  public boolean IGNORE_UNCHECKED_CAST;
  public boolean IGNORE_UNCHECKED_OVERRIDING;

  @NotNull
  static JCheckBox createSetting(@NotNull String cbText, final boolean option, @NotNull Pass<JCheckBox> pass) {
    final JCheckBox uncheckedCb = new JCheckBox(cbText, option);
    uncheckedCb.addActionListener(e -> pass.pass(uncheckedCb));
    return uncheckedCb;
  }

  @NotNull
  private static LocalQuickFix[] getChangeVariableTypeFixes(@NotNull PsiVariable parameter, @Nullable PsiType itemType, LocalQuickFix[] generifyFixes) {
    if (itemType instanceof PsiMethodReferenceType) return generifyFixes;
    LOG.assertTrue(parameter.isValid());
    final List<LocalQuickFix> result = new ArrayList<>();
    if (itemType != null) {
      for (ChangeVariableTypeQuickFixProvider fixProvider : Extensions.getExtensions(ChangeVariableTypeQuickFixProvider.EP_NAME)) {
        for (IntentionAction action : fixProvider.getFixes(parameter, itemType)) {
          if (action instanceof LocalQuickFix) {
            result.add((LocalQuickFix)action);
          }
        }
      }
    }

    if (generifyFixes.length > 0) {
      Collections.addAll(result, generifyFixes);
    }
    return result.toArray(new LocalQuickFix[result.size()]);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
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
      protected void registerProblem(@NotNull String message,
                                     @Nullable PsiElement callExpression,
                                     @NotNull PsiElement psiElement,
                                     @NotNull LocalQuickFix[] quickFixes) {
        final String rawExpression = isMethodCalledOnRawType(callExpression);
        if (rawExpression != null) {
          final String referenceName = ((PsiMethodCallExpression)callExpression).getMethodExpression().getReferenceName();
          message += ". Reason: '" + rawExpression + "' has raw type, so result of " + referenceName + " is erased";
        }
        holder.registerProblem(psiElement, message, quickFixes);
      }
    };
  }

  @NotNull
  protected LocalQuickFix[] createFixes() {
    return LocalQuickFix.EMPTY_ARRAY;
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
    private final LocalQuickFix[] myGenerifyFixes;

    UncheckedWarningsVisitor(boolean onTheFly, @NotNull LanguageLevel level) {
      myOnTheFly = onTheFly;
      myLanguageLevel = level;
      myGenerifyFixes = onTheFly ? createFixes() : LocalQuickFix.EMPTY_ARRAY;
    }

    protected abstract void registerProblem(@NotNull String message,
                                            PsiElement callExpression,
                                            @NotNull PsiElement psiElement,
                                            @NotNull LocalQuickFix[] quickFixes);


    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION) return;
      final JavaResolveResult result = expression.advancedResolve(false);
      if (JavaGenericsUtil.isUncheckedWarning(expression, result, myLanguageLevel)) {
        registerProblem("Unchecked generics array creation for varargs parameter", null, expression, LocalQuickFix.EMPTY_ARRAY);
      }
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION) return;
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      if (classReference != null && JavaGenericsUtil.isUncheckedWarning(classReference, expression.resolveMethodGenerics(), myLanguageLevel)) {
        registerProblem("Unchecked generics array creation for varargs parameter", expression, classReference, LocalQuickFix.EMPTY_ARRAY);
      }
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
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
          JavaErrorMessages.message("generics.unchecked.cast", JavaHighlightUtil.formatType(exprType), JavaHighlightUtil
            .formatType(castType));
        registerProblem(description, operand, expression, myGenerifyFixes);
      }
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (IGNORE_UNCHECKED_CALL) return;
      final JavaResolveResult result = expression.advancedResolve(false);
      final String description = getUncheckedCallDescription(expression, result);
      if (description != null) {
        final PsiElement referenceNameElement = expression.getReferenceNameElement();
        registerProblem(description, expression, referenceNameElement != null ? referenceNameElement : expression, myGenerifyFixes);
      }
    }

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      final JavaResolveResult result = callExpression.resolveMethodGenerics();
      final String description = getUncheckedCallDescription(callExpression, result);
      if (description != null) {
        if (IGNORE_UNCHECKED_CALL) return;
        final PsiExpression element = callExpression instanceof PsiMethodCallExpression
                                         ? ((PsiMethodCallExpression)callExpression).getMethodExpression()
                                         : callExpression;
        registerProblem(description, null, element, myGenerifyFixes);
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
                  checkRawToGenericsAssignment(expression, expression, parameterType, expressionType, () -> myGenerifyFixes);
                }
              }
            }
          }
        }
      }
    }

    @Override
    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null || initializer instanceof PsiArrayInitializerExpression) return;
      final PsiType initializerType = initializer.getType();
      checkRawToGenericsAssignment(initializer, initializer, variable.getType(), initializerType,
                                   () -> myOnTheFly ? getChangeVariableTypeFixes(variable, initializerType, myGenerifyFixes) : LocalQuickFix.EMPTY_ARRAY);
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      final PsiParameter parameter = statement.getIterationParameter();
      final PsiType parameterType = parameter.getType();
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) return;
      final PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
      checkRawToGenericsAssignment(parameter, iteratedValue, parameterType, itemType,
                                   () -> myOnTheFly ? getChangeVariableTypeFixes(parameter, itemType, myGenerifyFixes) : LocalQuickFix.EMPTY_ARRAY);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
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
            return getChangeVariableTypeFixes((PsiVariable)element, rType, myGenerifyFixes);
          }
        }

        return LocalQuickFix.EMPTY_ARRAY;
      });
    }

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression arrayInitializer) {
      super.visitArrayInitializerExpression(arrayInitializer);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      final PsiType type = arrayInitializer.getType();
      if (!(type instanceof PsiArrayType)) return;
      final PsiType componentType = ((PsiArrayType)type).getComponentType();


      boolean arrayTypeFixChecked = false;
      VariableArrayTypeFix fix = null;

      final PsiExpression[] initializers = arrayInitializer.getInitializers();
      for (PsiExpression expression : initializers) {
        final PsiType itemType = expression.getType();

        if (itemType == null) continue;
        if (!TypeConversionUtil.isAssignable(componentType, itemType)) continue;
        if (JavaGenericsUtil.isRawToGeneric(componentType, itemType)) {
          String description = JavaErrorMessages.message("generics.unchecked.assignment",
                                                         JavaHighlightUtil.formatType(itemType),
                                                         JavaHighlightUtil.formatType(componentType));
          if (!arrayTypeFixChecked) {
            final PsiType checkResult = JavaHighlightUtil.sameType(initializers);
            fix = checkResult != null ? VariableArrayTypeFix.createFix(arrayInitializer, checkResult) : null;
            arrayTypeFixChecked = true;
          }

          if (fix != null) {
            registerProblem(description, null, expression, new LocalQuickFix[]{fix});
          }
        }
      }
    }

    private void checkRawToGenericsAssignment(@NotNull PsiElement parameter,
                                              PsiExpression expression, PsiType parameterType,
                                              PsiType itemType,
                                              final Supplier<LocalQuickFix[]> fixesSupplier) {
      if (parameterType == null || itemType == null) return;
      if (!TypeConversionUtil.isAssignable(parameterType, itemType)) return;
      if (JavaGenericsUtil.isRawToGeneric(parameterType, itemType)) {
        String description = JavaErrorMessages.message("generics.unchecked.assignment",
                                                       JavaHighlightUtil.formatType(itemType),
                                                       JavaHighlightUtil.formatType(parameterType));
        registerProblem(description, expression, parameter, fixesSupplier.get());
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
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
              final String message = JavaErrorMessages.message("unchecked.overriding.incompatible.return.type",
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
    public void visitReturnStatement(PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      final PsiType returnType = PsiTypesUtil.getMethodReturnType(statement);
      if (returnType != null && !PsiType.VOID.equals(returnType)) {
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
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      super.visitLambdaExpression(expression);

      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      PsiElement body = expression.getBody();
      if (body instanceof PsiExpression) {
        PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(expression);
        if (interfaceReturnType != null && !PsiType.VOID.equals(interfaceReturnType)) {
          PsiType type = ((PsiExpression)body).getType();
          if (type != null) {
            checkRawToGenericsAssignment(body, (PsiExpression)body, interfaceReturnType, type, () -> LocalQuickFix.EMPTY_ARRAY);
          }
        }
      }
    }

    @Nullable
    private String getUncheckedCallDescription(PsiElement place, JavaResolveResult resolveResult) {
      final PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiMethod)) return null;
      final PsiMethod method = (PsiMethod)element;
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      if (!PsiUtil.isRawSubstitutor(method, substitutor)) {
        if (JavaVersionService.getInstance().isAtLeast(place, JavaSdkVersion.JDK_1_8)) {
          for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(method)) {
            final PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
            if (extendsListTypes.length > 0) {
              final PsiType subst = substitutor.substitute(parameter);
              for (PsiClassType classType : extendsListTypes) {
                if (JavaGenericsUtil.isRawToGeneric(substitutor.substitute(classType), subst)) {
                  return JavaErrorMessages.message("generics.unchecked.call", JavaHighlightUtil.formatMethod(method));
                }
              }
            }
          }
        }
        return null;
      }
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      for (final PsiParameter parameter : parameters) {
        final PsiType parameterType = parameter.getType();
        if (parameterType.accept(new PsiTypeVisitor<Boolean>() {
          @Override
          public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
            return Boolean.FALSE;
          }

          @Override
          public Boolean visitArrayType(PsiArrayType arrayType) {
            return arrayType.getComponentType().accept(this);
          }

          @Override
          public Boolean visitClassType(PsiClassType classType) {
            PsiClassType.ClassResolveResult result = classType.resolveGenerics();
            PsiClass psiClass = result.getElement();
            if (psiClass instanceof PsiTypeParameter) {
              if (((PsiTypeParameter)psiClass).getOwner() == method) return Boolean.FALSE;
              return substitutor.substitute((PsiTypeParameter)psiClass) == null ? Boolean.TRUE : Boolean.FALSE;
            }
            if (psiClass != null) {
              PsiSubstitutor typeSubstitutor = result.getSubstitutor();
              for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(psiClass)) {
                PsiType psiType = typeSubstitutor.substitute(parameter);
                if (psiType != null && psiType.accept(this).booleanValue()) return Boolean.TRUE;
              }
            }
            return Boolean.FALSE;
          }

          @Override
          public Boolean visitWildcardType(PsiWildcardType wildcardType) {
            PsiType bound = wildcardType.getBound();
            if (bound != null) return bound.accept(this);
            return Boolean.TRUE;
          }

          @Override
          public Boolean visitEllipsisType(PsiEllipsisType ellipsisType) {
            return ellipsisType.getComponentType().accept(this);
          }
        }).booleanValue()) {
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
          PsiType type = elementFactory.createType(method.getContainingClass(), substitutor);
          return JavaErrorMessages.message("generics.unchecked.call.to.member.of.raw.type",
                                                         JavaHighlightUtil.formatMethod(method),
                                                         JavaHighlightUtil.formatType(type));
        }
      }
      return null;
    }
  }
}
