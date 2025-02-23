// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.core.JavaPsiVariableUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

final class StatementChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  StatementChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

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

  void checkForeachExpressionTypeIsIterable(@NotNull PsiExpression expression) {
    if (!shouldReportForeachNotApplicable(expression)) return;
    if (expression.getType() == null) return;
    PsiType itemType = JavaGenericsUtil.getCollectionItemType(expression);
    if (itemType == null) {
      myVisitor.report(JavaErrorKinds.FOREACH_NOT_APPLICABLE.create(expression));
    }
  }

  private static boolean shouldReportForeachNotApplicable(@NotNull PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiForeachStatementBase parentForEach)) return false;

    PsiExpression iteratedValue = parentForEach.getIteratedValue();
    if (iteratedValue != expression) return false;

    // Ignore if the type of the value which is being iterated over is not resolved yet
    PsiType iteratedValueType = iteratedValue.getType();
    return iteratedValueType == null || !PsiTypesUtil.hasUnresolvedComponents(iteratedValueType);
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

  void checkTryResourceIsAutoCloseable(@NotNull PsiResourceListElement resource) {
    PsiType type = resource.getType();
    if (type == null) return;

    PsiElementFactory factory = myVisitor.factory();
    PsiClassType autoCloseable = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, resource.getResolveScope());
    if (TypeConversionUtil.isAssignable(autoCloseable, type)) return;
    if (myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(autoCloseable, type, resource)) return;

    myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
      resource, new JavaIncompatibleTypeErrorContext(autoCloseable, type)));
  }
  
  void checkBreakTarget(@NotNull PsiBreakStatement statement) {
    PsiIdentifier label = statement.getLabelIdentifier();
    PsiStatement target = statement.findExitedStatement();
    if (target == null) {
      if (label != null) {
        myVisitor.report(JavaErrorKinds.LABEL_UNRESOLVED.create(label));
      } else {
        myVisitor.report(JavaErrorKinds.BREAK_OUTSIDE_SWITCH_OR_LOOP.create(statement));
      }
      return;
    }

    if (myVisitor.isApplicable(JavaFeature.ENHANCED_SWITCH)) {
      PsiSwitchExpression expression = PsiImplUtil.findEnclosingSwitchExpression(statement);
      if (expression != null && PsiTreeUtil.isAncestor(target, expression, true)) {
        myVisitor.report(JavaErrorKinds.BREAK_OUT_OF_SWITCH_EXPRESSION.create(statement));
      }
    }
  }
  
  void checkContinueTarget(@NotNull PsiContinueStatement statement) {
    PsiIdentifier label = statement.getLabelIdentifier();
    PsiStatement target = statement.findContinuedStatement();
    if (target == null) {
      if (label != null) {
        myVisitor.report(JavaErrorKinds.LABEL_UNRESOLVED.create(label));
      } else {
        myVisitor.report(JavaErrorKinds.CONTINUE_OUTSIDE_LOOP.create(statement));
      }
      return;
    }
    if (label != null && !(target instanceof PsiLoopStatement)) {
      myVisitor.report(JavaErrorKinds.LABEL_MUST_BE_LOOP.create(statement, label));
      return;
    }

    if (myVisitor.isApplicable(JavaFeature.ENHANCED_SWITCH)) {
      PsiSwitchExpression expression = PsiImplUtil.findEnclosingSwitchExpression(statement);
      if (expression != null && PsiTreeUtil.isAncestor(target, expression, true)) {
        myVisitor.report(JavaErrorKinds.CONTINUE_OUT_OF_SWITCH_EXPRESSION.create(statement));
      }
    }
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

  void checkReturnStatement(@NotNull PsiReturnStatement statement) {
    if (myVisitor.isApplicable(JavaFeature.ENHANCED_SWITCH) && PsiImplUtil.findEnclosingSwitchExpression(statement) != null) {
      myVisitor.report(JavaErrorKinds.RETURN_OUTSIDE_SWITCH_EXPRESSION.create(statement));
      return;
    }
    PsiElement parent = PsiTreeUtil.getParentOfType(statement, PsiFile.class, PsiClassInitializer.class,
                                                    PsiLambdaExpression.class, PsiMethod.class);
    if (parent instanceof PsiMethod method) {
      if (JavaPsiRecordUtil.isCompactConstructor(method)) {
        myVisitor.report(JavaErrorKinds.RETURN_COMPACT_CONSTRUCTOR.create(statement));
        return;
      }
      if (method.isConstructor()) {
        PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
        if (constructorCall != null && statement.getTextOffset() < constructorCall.getTextOffset()) {
          myVisitor.report(JavaErrorKinds.RETURN_BEFORE_EXPLICIT_CONSTRUCTOR_CALL.create(statement, constructorCall));
          return;
        }
      }
    }
    if (parent != null) {
      checkReturnStatementType(statement, parent);
    }
  }

  void checkReturnStatementType(@NotNull PsiReturnStatement statement, @NotNull PsiElement parent) {
    if (parent instanceof PsiCodeFragment || parent instanceof PsiLambdaExpression) return;
    PsiMethod method = tryCast(parent, PsiMethod.class);
    if (method == null && !(parent instanceof ServerPageFile)) {
      myVisitor.report(JavaErrorKinds.RETURN_OUTSIDE_METHOD.create(statement));
      return;
    }
    PsiType returnType = method != null ? method.getReturnType() : null/*JSP page returns void*/;
    boolean isMethodVoid = returnType == null || PsiTypes.voidType().equals(returnType);
    PsiExpression returnValue = statement.getReturnValue();
    if (returnValue != null) {
      PsiType valueType = RefactoringChangeUtil.getTypeByExpression(returnValue);
      if (isMethodVoid) {
        boolean constructor = method != null && method.isConstructor();
        if (constructor) {
          PsiClass containingClass = method.getContainingClass();
          if (containingClass != null && !method.getName().equals(containingClass.getName())) {
            return;
          }
        }
        var kind = constructor ? JavaErrorKinds.RETURN_FROM_CONSTRUCTOR : JavaErrorKinds.RETURN_FROM_VOID_METHOD;
        myVisitor.report(kind.create(statement, method));
      }
      else {
        myVisitor.myExpressionChecker.checkAssignability(returnType, valueType, returnValue, returnValue);
      }
    }
    else if (!isMethodVoid && !PsiTreeUtil.hasErrorElements(statement)) {
      myVisitor.report(JavaErrorKinds.RETURN_VALUE_MISSING.create(statement, method));
    }
  }

  void checkNotAStatement(@NotNull PsiStatement statement) {
    if (PsiUtil.isStatement(statement)) return;
    if (PsiUtilCore.hasErrorElementChild(statement)) {
      boolean allowedError = false;
      if (statement instanceof PsiExpressionStatement) {
        PsiElement[] children = statement.getChildren();
        if (children[0] instanceof PsiExpression && children[1] instanceof PsiErrorElement errorElement &&
            errorElement.getErrorDescription().equals(JavaPsiBundle.message("expected.semicolon"))) {
          allowedError = true;
        }
      }
      if (!allowedError) return;
    }
    boolean isDeclarationNotAllowed = false;
    if (statement instanceof PsiDeclarationStatement) {
      PsiElement parent = statement.getParent();
      isDeclarationNotAllowed = parent instanceof PsiIfStatement || parent instanceof PsiLoopStatement;
    }
    var kind = isDeclarationNotAllowed ? JavaErrorKinds.STATEMENT_DECLARATION_NOT_ALLOWED : JavaErrorKinds.STATEMENT_BAD_EXPRESSION;
    myVisitor.report(kind.create(statement));
  }

  void checkAssertStatementTypes(@NotNull PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiAssertStatement assertStatement)) return;
    PsiType type = expression.getType();
    if (type == null) return;
    if (expression == assertStatement.getAssertCondition() && !TypeConversionUtil.isBooleanType(type)) {
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
        expression, new JavaIncompatibleTypeErrorContext(PsiTypes.booleanType(), type)));
    }
    else if (expression == assertStatement.getAssertDescription() && TypeConversionUtil.isVoidType(type)) {
      myVisitor.report(JavaErrorKinds.TYPE_VOID_NOT_ALLOWED.create(expression));
    }
  }

  void checkSynchronizedStatementType(@NotNull PsiExpression expression) {
    if (expression.getParent() instanceof PsiSynchronizedStatement synchronizedStatement &&
        expression == synchronizedStatement.getLockExpression()) {
      PsiType type = expression.getType();
      if (type == null) return;
      if (type instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(type)) {
        PsiClassType objectType = PsiType.getJavaLangObject(myVisitor.file().getManager(), expression.getResolveScope());
        myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(expression, new JavaIncompatibleTypeErrorContext(objectType, type)));
      }
    }
  }

  void checkVariableAlreadyDefined(@NotNull PsiVariable variable) {
    PsiVariable oldVariable = JavaPsiVariableUtil.findPreviousVariableDeclaration(variable);
    if (oldVariable != null) {
      myVisitor.report(JavaErrorKinds.VARIABLE_ALREADY_DEFINED.create(variable, oldVariable));
    }
  }
}
