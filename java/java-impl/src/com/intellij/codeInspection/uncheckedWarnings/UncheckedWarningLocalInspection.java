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

package com.intellij.codeInspection.uncheckedWarnings;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.GenerifyFileFix;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableArrayTypeFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UncheckedWarningLocalInspection extends BaseJavaLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "UNCHECKED_WARNING";
  public static final String DISPLAY_NAME = InspectionsBundle.message("unchecked.warning");
  @NonNls private static final String ID = "unchecked";
  private static final Logger LOG = Logger.getInstance("#" + UncheckedWarningLocalInspection.class);

  public boolean IGNORE_UNCHECKED_ASSIGNMENT = false;
  public boolean IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION = false;
  public boolean IGNORE_UNCHECKED_CALL = false;
  public boolean IGNORE_UNCHECKED_CAST = false;
  public boolean IGNORE_UNCHECKED_OVERRIDING = false;

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @NonNls
  public String getID() {
    return ID;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public void writeSettings(Element node) throws WriteExternalException {
    if (IGNORE_UNCHECKED_ASSIGNMENT ||
        IGNORE_UNCHECKED_CALL ||
        IGNORE_UNCHECKED_CAST ||
        IGNORE_UNCHECKED_OVERRIDING ||
        IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION) {
      super.writeSettings(node);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0,0);

    panel.add(createSetting("Ignore unchecked assignment", IGNORE_UNCHECKED_ASSIGNMENT, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
        IGNORE_UNCHECKED_ASSIGNMENT = cb.isSelected();
      }
    }), gc);

    panel.add(createSetting("Ignore unchecked generics array creation for vararg parameter", IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
          IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION = cb.isSelected();
      }
    }), gc);

    panel.add(createSetting("Ignore unchecked call as member of raw type", IGNORE_UNCHECKED_CALL, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
        IGNORE_UNCHECKED_CALL = cb.isSelected();
      }
    }), gc);

    panel.add(createSetting("Ignore unchecked cast", IGNORE_UNCHECKED_CAST, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
        IGNORE_UNCHECKED_CAST = cb.isSelected();
      }
    }), gc);

    panel.add(createSetting("Ignore unchecked overriding", IGNORE_UNCHECKED_OVERRIDING, new Pass<JCheckBox>() {
      @Override
      public void pass(JCheckBox cb) {
        IGNORE_UNCHECKED_OVERRIDING = cb.isSelected();
      }
    }), gc);

    gc.fill = GridBagConstraints.BOTH;
    gc.weighty = 1;
    panel.add(Box.createVerticalBox(), gc);

    return panel;
  }

  private static JCheckBox createSetting(final String cbText,
                                         final boolean option,
                                         final Pass<JCheckBox> pass) {
    final JCheckBox uncheckedCb = new JCheckBox(cbText, option);
    uncheckedCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        pass.pass(uncheckedCb);
      }
    });
    return uncheckedCb;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new UncheckedWarningsVisitor(isOnTheFly){
      @Override
      protected void registerProblem(@NotNull String message, @NotNull PsiElement psiElement, @NotNull LocalQuickFix[] quickFixes) {
        holder.registerProblem(psiElement, message, quickFixes);
      }
    };
  }

  public abstract class UncheckedWarningsVisitor extends JavaElementVisitor {
    private final boolean myOnTheFly;
    private final LocalQuickFix[] myGenerifyFixes;

    public UncheckedWarningsVisitor(boolean onTheFly) {
      myOnTheFly = onTheFly;
      myGenerifyFixes = onTheFly ? new LocalQuickFix[]{new GenerifyFileFix()} : LocalQuickFix.EMPTY_ARRAY;
    }

    protected abstract void registerProblem(@NotNull String message, @NotNull PsiElement psiElement, @NotNull LocalQuickFix[] quickFixes);


    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if (IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION) return;
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) return;
      if (GenericsHighlightUtil.isUncheckedWarning(expression, expression.resolve())) {
        registerProblem("Unchecked generics array creation for varargs parameter", expression, LocalQuickFix.EMPTY_ARRAY);
      }
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (IGNORE_UNCHECKED_GENERICS_ARRAY_CREATION) return;
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) return;
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      if (GenericsHighlightUtil.isUncheckedWarning(classReference, expression.resolveConstructor())) {
        registerProblem("Unchecked generics array creation for varargs parameter", classReference, LocalQuickFix.EMPTY_ARRAY);
      }
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      if (IGNORE_UNCHECKED_CAST) return;
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) return;
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement == null) return;
      final PsiType castType = typeElement.getType();
      final PsiExpression operand = expression.getOperand();
      if (operand == null) return;
      final PsiType exprType = operand.getType();
      if (exprType == null) return;
      if (!TypeConversionUtil.areTypesConvertible(exprType, castType)) return;
      if (GenericsHighlightUtil.isUncheckedCast(castType, exprType)) {
        final String description =
          JavaErrorMessages.message("generics.unchecked.cast", HighlightUtil.formatType(exprType), HighlightUtil.formatType(castType));
        registerProblem(description, expression, myGenerifyFixes);
      }
    }

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      if (!PsiUtil.isLanguageLevel5OrHigher(callExpression)) return;
      final JavaResolveResult result = callExpression.resolveMethodGenerics();
      final String description = getUncheckedCallDescription(result);
      if (description != null) {
        if (IGNORE_UNCHECKED_CALL) return;
        final PsiExpression element = callExpression instanceof PsiMethodCallExpression
                                         ? ((PsiMethodCallExpression)callExpression).getMethodExpression()
                                         : callExpression;
        registerProblem(description, element, myGenerifyFixes);
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
                final PsiType expressionType = substitutor.substitute(expression.getType());
                if (expressionType != null) {
                  checkRawToGenericsAssignment(expression, parameterType, expressionType, true, myGenerifyFixes);
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
      if (!PsiUtil.isLanguageLevel5OrHigher(variable)) return;
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null || initializer instanceof PsiArrayInitializerExpression) return;
      final PsiType initializerType = initializer.getType();
      checkRawToGenericsAssignment(initializer, variable.getType(), initializerType, true, myOnTheFly ? getChangeVariableTypeFixes(variable, initializerType) : LocalQuickFix.EMPTY_ARRAY);
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      if (!PsiUtil.isLanguageLevel5OrHigher(statement)) return;
      final PsiParameter parameter = statement.getIterationParameter();
      final PsiType parameterType = parameter.getType();
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) return;
      final PsiType itemType = GenericsHighlightUtil.getCollectionItemType(iteratedValue);
      if (!PsiUtil.isLanguageLevel5OrHigher(statement)) return;
      checkRawToGenericsAssignment(parameter, parameterType, itemType, true, myOnTheFly ? getChangeVariableTypeFixes(parameter, itemType) : null);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) return;
      if (!"=".equals(expression.getOperationSign().getText())) return;
      PsiExpression lExpr = expression.getLExpression();
      PsiExpression rExpr = expression.getRExpression();
      if (rExpr == null) return;
      PsiType lType = lExpr.getType();
      PsiType rType = rExpr.getType();
      if (rType == null) return;
      PsiVariable leftVar = null;
      if (lExpr instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)lExpr).resolve();
        if (element instanceof PsiVariable) {
          leftVar = (PsiVariable)element;
        }
      }
      checkRawToGenericsAssignment(rExpr, lType, rType, true, myOnTheFly && leftVar != null ? getChangeVariableTypeFixes(leftVar, rType) : LocalQuickFix.EMPTY_ARRAY);
    }

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression arrayInitializer) {
      super.visitArrayInitializerExpression(arrayInitializer);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      if (!PsiUtil.isLanguageLevel5OrHigher(arrayInitializer)) return;
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
        if (GenericsHighlightUtil.isRawToGeneric(componentType, itemType)) {
          String description = JavaErrorMessages.message("generics.unchecked.assignment",
                                                         HighlightUtil.formatType(itemType),
                                                         HighlightUtil.formatType(componentType));
          if (!arrayTypeFixChecked) {
            final PsiType checkResult = HighlightUtil.sameType(initializers);
            fix = checkResult != null ? new VariableArrayTypeFix(arrayInitializer, checkResult) : null;
            arrayTypeFixChecked = true;
          }

          if (fix != null) {
            registerProblem(description, expression, new LocalQuickFix[]{fix});
          }
        }
      }
    }

    private void checkRawToGenericsAssignment(@NotNull PsiElement parameter,
                                              PsiType parameterType,
                                              PsiType itemType,
                                              boolean checkAssignability,
                                              @NotNull LocalQuickFix[] quickFixes) {
      if (parameterType == null || itemType == null) return;
      if (checkAssignability && !TypeConversionUtil.isAssignable(parameterType, itemType)) return;
      if (GenericsHighlightUtil.isRawToGeneric(parameterType, itemType)) {
        String description = JavaErrorMessages.message("generics.unchecked.assignment",
                                                       HighlightUtil.formatType(itemType),
                                                       HighlightUtil.formatType(parameterType));
        registerProblem(description, parameter, quickFixes);
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (IGNORE_UNCHECKED_OVERRIDING) return;
      if (!PsiUtil.isLanguageLevel5OrHigher(method)) return;
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
            if (GenericsHighlightUtil.isRawToGeneric(baseReturnType, overriderReturnType)) {
              final String message = JavaErrorMessages.message("unchecked.overriding.incompatible.return.type",
                                                               HighlightUtil.formatType(overriderReturnType),
                                                               HighlightUtil.formatType(baseReturnType));

              final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
              LOG.assertTrue(returnTypeElement != null);
              registerProblem(message, returnTypeElement, LocalQuickFix.EMPTY_ARRAY);
            }
          }
        }
      }
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      if (IGNORE_UNCHECKED_ASSIGNMENT) return;
      if (!PsiUtil.isLanguageLevel5OrHigher(statement)) return;
      final PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      if (method != null) {
        final PsiType returnType = method.getReturnType();
        if (returnType != null && returnType != PsiType.VOID) {
          final PsiExpression returnValue = statement.getReturnValue();
          if (returnValue != null) {
            final PsiType valueType = returnValue.getType();
            if (valueType != null) {
              checkRawToGenericsAssignment(returnValue, returnType, valueType,
                                           false,
                                           new LocalQuickFix[]{QuickFixFactory.getInstance().createMethodReturnFix(method, valueType, true)});
            }
          }
        }
      }
    }


    @Nullable
    private String getUncheckedCallDescription(JavaResolveResult resolveResult) {
      final PsiMethod method = (PsiMethod)resolveResult.getElement();
      if (method == null) return null;
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      for (final PsiParameter parameter : parameters) {
        final PsiType parameterType = parameter.getType();
        if (parameterType.accept(new PsiTypeVisitor<Boolean>() {
          public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
            return Boolean.FALSE;
          }

          public Boolean visitArrayType(PsiArrayType arrayType) {
            return arrayType.getComponentType().accept(this);
          }

          public Boolean visitClassType(PsiClassType classType) {
            PsiClass psiClass = classType.resolve();
            if (psiClass instanceof PsiTypeParameter) {
              return substitutor.substitute((PsiTypeParameter)psiClass) == null ? Boolean.TRUE : Boolean.FALSE;
            }
            PsiType[] parameters = classType.getParameters();
            for (PsiType parameter : parameters) {
              if (parameter.accept(this).booleanValue()) return Boolean.TRUE;
            }
            return Boolean.FALSE;
          }

          public Boolean visitWildcardType(PsiWildcardType wildcardType) {
            PsiType bound = wildcardType.getBound();
            if (bound != null) return bound.accept(this);
            return Boolean.FALSE;
          }

          public Boolean visitEllipsisType(PsiEllipsisType ellipsisType) {
            return ellipsisType.getComponentType().accept(this);
          }
        }).booleanValue()) {
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
          PsiType type = elementFactory.createType(method.getContainingClass(), substitutor);
          return JavaErrorMessages.message("generics.unchecked.call.to.member.of.raw.type",
                                                         HighlightUtil.formatMethod(method),
                                                         HighlightUtil.formatType(type));
        }
      }
      return null;
    }
  }

  public static LocalQuickFix[] getChangeVariableTypeFixes(PsiVariable parameter, PsiType itemType) {
    final List<LocalQuickFix> result = new ArrayList<LocalQuickFix>();
    for (ChangeVariableTypeQuickFixProvider fixProvider : Extensions.getExtensions(ChangeVariableTypeQuickFixProvider.EP_NAME)) {
      for (IntentionAction action : fixProvider.getFixes(parameter, itemType)) {
        if (action instanceof LocalQuickFix) {
          result.add((LocalQuickFix)action);
        }
      }
    }
    return result.toArray(new LocalQuickFix[result.size()]);
  }
}
