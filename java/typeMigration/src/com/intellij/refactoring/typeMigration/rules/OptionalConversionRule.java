// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class OptionalConversionRule extends TypeConversionRule {
  private static final Map<String, String> GET_METHODS = Map.of(
    CommonClassNames.JAVA_UTIL_OPTIONAL, "orElse(null)",
    "java.util.OptionalInt", "getAsInt()",
    "java.util.OptionalLong", "getAsLong()",
    "java.util.OptionalDouble", "getAsDouble()");

  @Override
  public @Nullable TypeConversionDescriptorBase findConversion(PsiType from,
                                                               PsiType to,
                                                               PsiMember member,
                                                               PsiExpression context,
                                                               TypeMigrationLabeler labeler) {
    PsiType optionalElementType = OptionalUtil.getOptionalElementType(to);
    if (optionalElementType == null) return null;
    if (!from.equals(optionalElementType)) {
      return null;
    }
    PsiClass optionalClass = PsiUtil.resolveClassInClassTypeOnly(to);
    if (optionalClass == null) return null;
    String qualifiedName = optionalClass.getQualifiedName();
    if (qualifiedName == null) return null;
    PsiElement parent = context.getParent();
    TypeMigrationUsageInfo root = labeler.getCurrentRoot();
    PsiElement element = root == null ? null : root.getElement();
    if (element != null && context instanceof PsiReferenceExpression ref) {
      PsiExpression qualifier = ref.getQualifierExpression();
      if (qualifier instanceof PsiReferenceExpression qualifierRef && qualifierRef.isReferenceTo(element)) {
        return new TypeConversionDescriptor("$val$", "$val$.get()", qualifier);
      }
    }
    if (element != null && context instanceof PsiReferenceExpression ref && ref.isReferenceTo(element)) {
      if (parent instanceof PsiBinaryExpression binOp) {
        IElementType tokenType = binOp.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE)) {
          PsiExpression lOp = binOp.getLOperand();
          PsiExpression rOp = binOp.getROperand();
          if (rOp != null) {
            if (ExpressionUtils.isNullLiteral(rOp)) {
              return new TypeConversionDescriptor("$val$" + binOp.getOperationSign().getText() + rOp.getText(),
                                                  tokenType.equals(JavaTokenType.EQEQ) ? "$val$.isEmpty()" : "$val$.isPresent()", binOp);
            }
            if (ExpressionUtils.isNullLiteral(lOp)) {
              return new TypeConversionDescriptor(lOp.getText() + binOp.getOperationSign().getText() + "$val$",
                                                  tokenType.equals(JavaTokenType.EQEQ) ? "$val$.isEmpty()" : "$val$.isPresent()", binOp);
            }
          }
        }
      }
      return new TypeConversionDescriptor("$val$", "$val$." + GET_METHODS.get(qualifiedName), context);
    }
    else if (parent instanceof PsiAssignmentExpression assignment && assignment.getRExpression() == context
             && element != null &&
             assignment.getLExpression() instanceof PsiReferenceExpression ref && ref.isReferenceTo(element) &&
             assignment.getOperationTokenType().equals(JavaTokenType.PLUSEQ) && TypeUtils.isJavaLangString(optionalElementType)) {
      String varName = new VariableNameGenerator(context, VariableKind.PARAMETER).byName(ref.getText()).byType(optionalElementType).generate(true);
      return new TypeConversionDescriptor("$val$+=$operand$", "$val$=$val$.map(" + varName + "->" + varName + "+$operand$)", assignment);
    }
    else if (context instanceof PsiAssignmentExpression assignment) {
      String compoundOp = assignment.getOperationSign().getText();
      String op = compoundOp.substring(0, compoundOp.length() - 1);
      if (!op.isEmpty()) {
        return new TypeConversionDescriptor(
          "$val$" + compoundOp + "$operand$",
          "$val$=" + qualifiedName + ".of($val$." + GET_METHODS.get(qualifiedName) + op + "$operand$)", context);
      }
    }
    else if (context instanceof PsiUnaryExpression unary &&
             (context instanceof PsiPrefixExpression || ExpressionUtils.isVoidContext(unary))) {
      IElementType tokenType = unary.getOperationTokenType();
      String op = tokenType.equals(JavaTokenType.PLUSPLUS) ? "+" :
                  tokenType.equals(JavaTokenType.MINUSMINUS) ? "-" : null;
      if (op != null) {
        String orig = "$val$";
        if (context instanceof PsiPrefixExpression) {
          orig = op + op + orig;
        }
        else {
          orig = orig + op + op;
        }
        return new TypeConversionDescriptor(orig,
                                            "$val$=" + qualifiedName + ".of($val$." + GET_METHODS.get(qualifiedName) + op + "1)", context);
      }
    }
    if (ExpressionUtils.isVoidContext(context)) {
      return new TypeConversionDescriptor("$val$", "$val$", context);
    }
    return wrap(context, qualifiedName);
  }

  private static @NotNull TypeConversionDescriptor wrap(PsiExpression context, String qualifiedName) {
    if (ExpressionUtils.isNullLiteral(context)) {
      return new TypeConversionDescriptor("$val$", qualifiedName + ".empty()", context);
    }
    if (NullabilityUtil.getExpressionNullability(context, true) == Nullability.NOT_NULL) {
      return new TypeConversionDescriptor("$val$", qualifiedName + ".of($val$)", context);
    }
    return new TypeConversionDescriptor("$val$", qualifiedName + ".ofNullable($val$)", context);
  }

  @Override
  public boolean shouldConvertNullInitializer(PsiType from, PsiType to, PsiExpression context) {
    return from.equals(OptionalUtil.getOptionalElementType(to));
  }
}
