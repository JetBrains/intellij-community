/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ExceptionUtils {

  private ExceptionUtils() { }

  private static final @Unmodifiable Set<String> s_genericExceptionTypes;

  static {
    s_genericExceptionTypes = Set.of(CommonClassNames.JAVA_LANG_THROWABLE,
    CommonClassNames.JAVA_LANG_EXCEPTION,
    CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION,
    CommonClassNames.JAVA_LANG_ERROR);
  }

  public static @NotNull Set<PsiClassType> calculateExceptionsThrown(@Nullable PsiElement element) {
    return calculateExceptionsThrown(element, new LinkedHashSet<>(5));
  }

  public static @NotNull Set<PsiClassType> calculateExceptionsThrown(@Nullable PsiElement element, @NotNull Set<PsiClassType> out) {
    if (element == null) return out;
    if (element instanceof PsiResourceList resourceList) {
      for (PsiResourceListElement resource : resourceList) {
        out.addAll(ExceptionUtil.getCloserExceptions(resource));
      }
    }
    final ExceptionsThrownVisitor visitor = new ExceptionsThrownVisitor(out);
    element.accept(visitor);
    return out;
  }

  public static boolean isGenericExceptionClass(@Nullable PsiType exceptionType) {
    if (!(exceptionType instanceof PsiClassType classType)) {
      return false;
    }
    final String className = classType.getCanonicalText();
    return s_genericExceptionTypes.contains(className);
  }

  public static boolean isThrowableRethrown(PsiParameter throwable, PsiCodeBlock catchBlock) {
    final PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(catchBlock);
    if (!(lastStatement instanceof PsiThrowStatement throwStatement)) {
      return false;
    }
    final PsiExpression expression = throwStatement.getException();
    if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
      return false;
    }
    final PsiElement element = referenceExpression.resolve();
    return throwable.equals(element);
  }

  static boolean statementThrowsException(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiAssertStatement ||
        statement instanceof PsiReturnStatement ||
        statement instanceof PsiExpressionStatement ||
        statement instanceof PsiExpressionListStatement ||
        statement instanceof PsiForeachStatement ||
        statement instanceof PsiDeclarationStatement ||
        statement instanceof PsiEmptyStatement ||
        statement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    else if (statement instanceof PsiThrowStatement) {
      return true;
    }
    else if (statement instanceof PsiForStatement) {
      return forStatementThrowsException((PsiForStatement)statement);
    }
    else if (statement instanceof PsiWhileStatement) {
      return whileStatementThrowsException((PsiWhileStatement)statement);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      return doWhileThrowsException((PsiDoWhileStatement)statement);
    }
    else if (statement instanceof PsiSynchronizedStatement synchronizedStatement) {
      final PsiCodeBlock body = synchronizedStatement.getBody();
      return blockThrowsException(body);
    }
    else if (statement instanceof PsiBlockStatement blockStatement) {
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      return blockThrowsException(codeBlock);
    }
    else if (statement instanceof PsiLabeledStatement labeledStatement) {
      final PsiStatement statementLabeled = labeledStatement.getStatement();
      return statementThrowsException(statementLabeled);
    }
    else if (statement instanceof PsiIfStatement) {
      return ifStatementThrowsException((PsiIfStatement)statement);
    }
    else if (statement instanceof PsiTryStatement) {
      return tryStatementThrowsException((PsiTryStatement)statement);
    }
    else if (statement instanceof PsiSwitchStatement) {
      return false;
    }
    else {
      // unknown statement type
      return false;
    }
  }

  static boolean blockThrowsException(@Nullable PsiCodeBlock block) {
    if (block == null) {
      return false;
    }
    final PsiStatement[] statements = block.getStatements();
    for (PsiStatement statement : statements) {
      if (statementThrowsException(statement)) {
        return true;
      }
    }
    return false;
  }

  private static boolean tryStatementThrowsException(PsiTryStatement tryStatement) {
    final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    if (catchBlocks.length == 0) {
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (blockThrowsException(tryBlock)) {
        return true;
      }
    }
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    return blockThrowsException(finallyBlock);
  }

  private static boolean ifStatementThrowsException(PsiIfStatement ifStatement) {
    return statementThrowsException(ifStatement.getThenBranch()) && statementThrowsException(ifStatement.getElseBranch());
  }

  private static boolean doWhileThrowsException(PsiDoWhileStatement doWhileStatement) {
    return statementThrowsException(doWhileStatement.getBody());
  }

  private static boolean whileStatementThrowsException(PsiWhileStatement whileStatement) {
    final PsiExpression condition = whileStatement.getCondition();
    if (BoolUtils.isTrue(condition)) {
      final PsiStatement body = whileStatement.getBody();
      if (statementThrowsException(body)) {
        return true;
      }
    }
    return false;
  }

  private static boolean forStatementThrowsException(PsiForStatement forStatement) {
    final PsiStatement initialization = forStatement.getInitialization();
    if (statementThrowsException(initialization)) {
      return true;
    }
    final PsiExpression test = forStatement.getCondition();
    if (BoolUtils.isTrue(test)) {
      final PsiStatement body = forStatement.getBody();
      if (statementThrowsException(body)) {
        return true;
      }
      final PsiStatement update = forStatement.getUpdate();
      if (statementThrowsException(update)) {
        return true;
      }
    }
    return false;
  }

  public static Set<PsiType> getExceptionTypesHandled(PsiTryStatement statement) {
    final Set<PsiType> out = new HashSet<>(5);
    for (PsiParameter parameter : statement.getCatchBlockParameters()) {
      final PsiType type = parameter.getType();
      if (type instanceof PsiDisjunctionType disjunctionType) {
        out.addAll(disjunctionType.getDisjunctions());
      } else {
        out.add(type);
      }
    }
    return out;
  }

  public static boolean isExceptionArgument(@NotNull PsiExpression expression) {
    final PsiNewExpression newExpression =
      PsiTreeUtil.getParentOfType(expression, PsiNewExpression.class, true, PsiCodeBlock.class, PsiClass.class);
    if (newExpression != null) {
      final PsiType newExpressionType = newExpression.getType();
      if (InheritanceUtil.isInheritor(newExpressionType, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return true;
      }
    } else {
      final PsiMethodCallExpression methodCallExpression =
        PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class, true, PsiCodeBlock.class, PsiClass.class);
      if (JavaPsiConstructorUtil.isConstructorCall(methodCallExpression)) {
        final PsiMethod constructor = methodCallExpression.resolveMethod();
        return constructor != null && InheritanceUtil.isInheritor(constructor.getContainingClass(), CommonClassNames.JAVA_LANG_THROWABLE);
      }
    }
    return false;
  }

  private static final class ExceptionsThrownVisitor extends JavaRecursiveElementWalkingVisitor {

    private final Set<PsiClassType> myThrownExceptionTypes;

    private ExceptionsThrownVisitor(Set<PsiClassType> thrownTypes) {
      myThrownExceptionTypes = thrownTypes;
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {}

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      final JavaResolveResult resolveResult = callExpression.resolveMethodGenerics();
      final PsiElement target = resolveResult.getElement();
      if (!(target instanceof PsiMethod)) {
        return;
      }
      final PsiClassType[] referencedTypes = ((PsiMethod)target).getThrowsList().getReferencedTypes();
      myThrownExceptionTypes.addAll(ExceptionUtil.collectSubstituted(resolveResult.getSubstitutor(),
                                                                     referencedTypes, callExpression.getResolveScope()));
    }

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      final PsiExpression exception = statement.getException();
      if (exception == null) {
        return;
      }
      final PsiType type = exception.getType();
      if (type instanceof PsiDisjunctionType disjunctionType) {
        for (PsiType disjunction : disjunctionType.getDisjunctions()) {
          if (disjunction instanceof PsiClassType) {
            myThrownExceptionTypes.add((PsiClassType)disjunction);
          }
        }
      }
      else if (PsiTypes.nullType().equals(type)) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());
        final PsiClassType npeType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION,
                                                                     statement.getResolveScope());
        myThrownExceptionTypes.add(npeType);
      }
      else if (type instanceof PsiClassType) {
        myThrownExceptionTypes.add((PsiClassType)type);
      }
    }

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement statement) {
      final Set<PsiType> exceptionsHandled = getExceptionTypesHandled(statement);

      for (PsiClassType resourceException : calculateExceptionsThrown(statement.getResourceList())) {
        if (!isExceptionHandled(exceptionsHandled, resourceException)) {
          myThrownExceptionTypes.add(resourceException);
        }
      }
      for (PsiClassType tryException : calculateExceptionsThrown(statement.getTryBlock())) {
        if (!isExceptionHandled(exceptionsHandled, tryException)) {
          myThrownExceptionTypes.add(tryException);
        }
      }
      calculateExceptionsThrown(statement.getFinallyBlock(), myThrownExceptionTypes);
      for (PsiCodeBlock catchBlock : statement.getCatchBlocks()) {
        calculateExceptionsThrown(catchBlock, myThrownExceptionTypes);
      }
    }

    private static boolean isExceptionHandled(Set<PsiType> exceptionsHandled, @NotNull PsiType thrownType) {
      return exceptionsHandled.contains(thrownType) ||
             exceptionsHandled.stream().anyMatch(exceptionHandled -> exceptionHandled.isAssignableFrom(thrownType));
    }
  }
}