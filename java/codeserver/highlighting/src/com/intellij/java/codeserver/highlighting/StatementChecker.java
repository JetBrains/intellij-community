// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class StatementChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  StatementChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkCaseStatement(@NotNull PsiSwitchLabelStatementBase statement) {
    PsiSwitchBlock switchBlock = statement.getEnclosingSwitchBlock();
    if (switchBlock == null) {
      myVisitor.report(JavaErrorKinds.STATEMENT_CASE_OUTSIDE_SWITCH.create(statement));
    }
  }

  void checkGuard(@NotNull PsiSwitchLabelStatementBase statement) {
    PsiExpression guardingExpr = statement.getGuardExpression();
    if (guardingExpr == null) return;
    myVisitor.checkFeature(guardingExpr, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS);
    if (myVisitor.hasErrorResults()) return;
    PsiCaseLabelElementList list = statement.getCaseLabelElementList();
    if (list != null) {
      if (!ContainerUtil.exists(list.getElements(), e -> e instanceof PsiPattern)) {
        myVisitor.report(JavaErrorKinds.GUARD_MISPLACED.create(guardingExpr));
        return;
      }
    }
    if (!TypeConversionUtil.isBooleanType(guardingExpr.getType())) {
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
        guardingExpr, new JavaIncompatibleTypeErrorContext(PsiTypes.booleanType(), guardingExpr.getType())));
      return;
    }
    Object constVal = JavaPsiFacade.getInstance(myVisitor.project()).getConstantEvaluationHelper().computeConstantExpression(guardingExpr);
    if (Boolean.FALSE.equals(constVal)) {
      myVisitor.report(JavaErrorKinds.GUARD_EVALUATED_TO_FALSE.create(guardingExpr));
    }
  }

  void checkLabelWithoutStatement(@NotNull PsiLabeledStatement statement) {
    if (statement.getStatement() == null) {
      myVisitor.report(JavaErrorKinds.LABEL_WITHOUT_STATEMENT.create(statement));
    }
  }

  void checkLabelAlreadyInUse(@NotNull PsiLabeledStatement statement) {
    PsiIdentifier identifier = statement.getLabelIdentifier();
    String text = identifier.getText();
    PsiElement element = statement;
    while (element != null) {
      if (element instanceof PsiMethod || element instanceof PsiClass) break;
      if (element instanceof PsiLabeledStatement labeledStatement && element != statement &&
          Objects.equals(labeledStatement.getLabelIdentifier().getText(), text)) {
        myVisitor.report(JavaErrorKinds.LABEL_DUPLICATE.create(statement));
        return;
      }
      element = element.getParent();
    }
  }

  void checkCatchTypeIsDisjoint(@NotNull PsiParameter parameter) {
    if (!(parameter.getType() instanceof PsiDisjunctionType)) return;

    List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    for (int i = 0, size = typeElements.size(); i < size; i++) {
      PsiClass class1 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(i).getType());
      if (class1 == null) continue;
      for (int j = i + 1; j < size; j++) {
        PsiClass class2 = PsiUtil.resolveClassInClassTypeOnly(typeElements.get(j).getType());
        if (class2 == null) continue;
        boolean sub = InheritanceUtil.isInheritorOrSelf(class1, class2, true);
        boolean sup = InheritanceUtil.isInheritorOrSelf(class2, class1, true);
        if (sub || sup) {
          PsiTypeElement element = typeElements.get(sub ? i : j);
          myVisitor.report(JavaErrorKinds.EXCEPTION_MUST_BE_DISJOINT.create(
            element, new JavaErrorKinds.SuperclassSubclassContext(sub ? class2 : class1, sub ? class1 : class2)));
          break;
        }
      }
    }
  }

  void checkExceptionAlreadyCaught(@NotNull PsiParameter parameter) {
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiCatchSection catchSection)) return;

    PsiCatchSection[] allCatchSections = catchSection.getTryStatement().getCatchSections();
    int startFrom = ArrayUtilRt.find(allCatchSections, catchSection) - 1;
    if (startFrom < 0) return;

    List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    for (PsiTypeElement typeElement : typeElements) {
      PsiClass catchClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
      if (catchClass == null) continue;

      for (int i = startFrom; i >= 0; i--) {
        PsiCatchSection upperCatchSection = allCatchSections[i];
        PsiType upperCatchType = upperCatchSection.getCatchType();

        boolean highlight = upperCatchType instanceof PsiDisjunctionType type
                            ? checkMultipleTypes(catchClass, type.getDisjunctions())
                            : checkSingleType(catchClass, upperCatchType);
        if (highlight) {
          myVisitor.report(JavaErrorKinds.EXCEPTION_ALREADY_CAUGHT.create(typeElement, upperCatchSection));
        }
      }
    }
  }

  void checkExceptionThrownInTry(@NotNull PsiParameter parameter,
                                 @NotNull Set<? extends PsiClassType> thrownTypes) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiCatchSection)) return;

    PsiType caughtType = parameter.getType();
    if (caughtType instanceof PsiClassType) {
      checkSimpleCatchParameter(parameter, thrownTypes, (PsiClassType)caughtType);
      return;
    }
    if (caughtType instanceof PsiDisjunctionType) {
      checkMultiCatchParameter(parameter, thrownTypes);
    }
  }

  private void checkSimpleCatchParameter(@NotNull PsiParameter parameter,
                                         @NotNull Collection<? extends PsiClassType> thrownTypes,
                                         @NotNull PsiClassType caughtType) {
    if (ExceptionUtil.isUncheckedExceptionOrSuperclass(caughtType)) return;

    for (PsiClassType exceptionType : thrownTypes) {
      if (exceptionType.isAssignableFrom(caughtType) || caughtType.isAssignableFrom(exceptionType)) return;
    }
    myVisitor.report(JavaErrorKinds.EXCEPTION_NEVER_THROWN_TRY.create(parameter, caughtType));
  }

  private void checkMultiCatchParameter(@NotNull PsiParameter parameter, @NotNull Collection<? extends PsiClassType> thrownTypes) {
    List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);

    for (PsiTypeElement typeElement : typeElements) {
      PsiType catchType = typeElement.getType();
      if (!(catchType instanceof PsiClassType classType)) continue;
      if (ExceptionUtil.isUncheckedExceptionOrSuperclass(classType)) continue;

      boolean used = false;
      for (PsiClassType exceptionType : thrownTypes) {
        if (exceptionType.isAssignableFrom(catchType) || catchType.isAssignableFrom(exceptionType)) {
          used = true;
          break;
        }
      }
      if (!used) {
        myVisitor.report(JavaErrorKinds.EXCEPTION_NEVER_THROWN_TRY_MULTI.create(typeElement, classType));
      }
    }
  }

  void checkForStatement(@NotNull PsiForStatement statement) {
    PsiStatement init = statement.getInitialization();
    if (init == null ||
        init instanceof PsiEmptyStatement ||
        init instanceof PsiDeclarationStatement declarationStatement &&
        ArrayUtil.getFirstElement(declarationStatement.getDeclaredElements()) instanceof PsiLocalVariable ||
        init instanceof PsiExpressionStatement ||
        init instanceof PsiExpressionListStatement) {
      return;
    }

    myVisitor.report(JavaErrorKinds.STATEMENT_INVALID.create(init));
  }


  private static boolean checkMultipleTypes(@NotNull PsiClass catchClass, @NotNull List<? extends PsiType> upperCatchTypes) {
    for (int i = upperCatchTypes.size() - 1; i >= 0; i--) {
      if (checkSingleType(catchClass, upperCatchTypes.get(i))) return true;
    }
    return false;
  }

  private static boolean checkSingleType(@NotNull PsiClass catchClass, @Nullable PsiType upperCatchType) {
    PsiClass upperCatchClass = PsiUtil.resolveClassInType(upperCatchType);
    return upperCatchClass != null && InheritanceUtil.isInheritorOrSelf(catchClass, upperCatchClass, true);
  }
}
