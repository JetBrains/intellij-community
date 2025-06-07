// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.resources;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public abstract class ResourceInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean insideTryAllowed;

  @SuppressWarnings("PublicField")
  public boolean anyMethodMayClose = true;

  public boolean ignoreResourcesWithClose = true;

  private static final CallMatcher CLOSE = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, "close");

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    defaultWriteSettings(node, "anyMethodMayClose", "ignoreResourcesWithClose");
    writeBooleanOption(node, "anyMethodMayClose", true);
    writeBooleanOption(node, "ignoreResourcesWithClose", true);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("insideTryAllowed", InspectionGadgetsBundle.message("allow.resource.to.be.opened.inside.a.try.block")),
      checkbox("anyMethodMayClose", InspectionGadgetsBundle.message("any.method.may.close.resource.argument")));
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message("resource.opened.not.closed.problem.descriptor", text);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ResourceVisitor();
  }

  private class ResourceVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isNotSafelyClosedResource(expression)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isNotSafelyClosedResource(expression)) {
        return;
      }
      registerNewExpressionError(expression, expression);
    }

    private boolean isNotSafelyClosedResource(PsiExpression expression) {
      if (!isResourceCreation(expression)) {
        return false;
      }
      final PsiVariable boundVariable = getVariable(expression);
      return !(boundVariable instanceof PsiResourceVariable) &&
             !isSafelyClosed(boundVariable, expression) &&
             !isResourceFactoryClosed(expression) &&
             !isResourceEscaping(boundVariable, expression) &&
             !isSafelyClosedResource(expression);
    }
  }

  @Contract(pure = true)
  protected boolean isSafelyClosedResource(@NotNull PsiExpression expression) {
    return CLOSE.test(ExpressionUtils.getCallForQualifier(expression));
  }

  @Contract(pure = true)
  protected abstract boolean isResourceCreation(PsiExpression expression);

  /**
   *  When passed to constructor of resource like object this parameter may take over ownership, so no need to track resource created by
   *  this constructor
   */
  @Contract(pure = true)
  protected boolean canTakeOwnership(@NotNull PsiExpression expression) {
    return isResourceCreation(expression);
  }

  protected boolean isResourceFactoryClosed(PsiExpression expression) {
    return false;
  }

  public static @Nullable PsiVariable getVariable(@NotNull PsiExpression expression) {
    final PsiElement parent = getParent(expression);
    if (parent instanceof PsiAssignmentExpression assignment) {
      final PsiExpression lhs = assignment.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
        return null;
      }
      final PsiElement referent = referenceExpression.resolve();
      if (!(referent instanceof PsiVariable)) {
        return null;
      }
      return (PsiVariable)referent;
    }
    if (parent instanceof PsiVariable) {
      return (PsiVariable)parent;
    }
    if (parent instanceof PsiConditionalExpression) {
      return getVariable((PsiExpression)parent);
    }
    return null;
  }

  private static PsiElement getParent(PsiExpression expression) {
    PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
    if (parent == null) {
      return null;
    }
    PsiElement grandParent = parent.getParent();
    final PsiType type = expression.getType();
    if (type == null) {
      return null;
    }
    while (parent instanceof PsiReferenceExpression && grandParent instanceof PsiMethodCallExpression methodCallExpression) {
      if (!type.equals(methodCallExpression.getType())) {
        return null;
      }
      parent = ParenthesesUtils.getParentSkipParentheses(grandParent);
      if (parent == null) {
        return null;
      }
      grandParent = parent.getParent();
    }
    return parent;
  }

  private boolean isSafelyClosed(@Nullable PsiVariable variable, PsiElement context) {
    if (variable == null) {
      return false;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(context, PsiStatement.class);
    if (statement == null) {
      return false;
    }
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (insideTryAllowed) {
      PsiStatement parentStatement = PsiTreeUtil.getParentOfType(statement, PsiStatement.class);
      while (parentStatement != null && !(parentStatement instanceof PsiTryStatement)) {
        parentStatement = PsiTreeUtil.getParentOfType(parentStatement, PsiStatement.class);
      }
      if (parentStatement != null) {
        final PsiTryStatement tryStatement = (PsiTryStatement)parentStatement;
        if (isResourceClosedInFinally(tryStatement, variable)) {
          return true;
        }
      }
    }
    while (nextStatement != null && !isSignificant(nextStatement)) {
      nextStatement = PsiTreeUtil.getNextSiblingOfType(nextStatement, PsiStatement.class);
    }
    while (nextStatement == null) {
      statement = PsiTreeUtil.getParentOfType(statement, PsiStatement.class, true);
      if (statement == null) {
        return false;
      }
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        statement = (PsiStatement)parent;
      }
      nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    }
    if (!(nextStatement instanceof PsiTryStatement tryStatement)) {
      // exception in next statement can prevent closing of the resource
      return isResourceClose(nextStatement, variable);
    }
    if (isResourceClosedInFinally(tryStatement, variable)) {
      return true;
    }
    return isResourceClose(nextStatement, variable);
  }

  private static boolean isSignificant(@NotNull PsiStatement statement) {
    final Ref<Boolean> result = new Ref<>(Boolean.TRUE);
    statement.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        super.visitExpression(expression);
        result.set(Boolean.FALSE);
        stopWalking();
      }
    });
    return !result.get().booleanValue();
  }

  boolean isResourceClosedInFinally(@NotNull PsiTryStatement tryStatement, @NotNull PsiVariable variable) {
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock == null) {
      return false;
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return false;
    }
    final CloseVisitor visitor = new CloseVisitor(variable);
    finallyBlock.accept(visitor);
    return visitor.containsClose();
  }

  private boolean isResourceClose(PsiStatement statement, PsiVariable variable) {
    if (statement instanceof PsiExpressionStatement expressionStatement) {
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      return isResourceClose(methodCallExpression, variable);
    }
    if (statement instanceof PsiTryStatement tryStatement) {
      final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (isResourceClose(ControlFlowUtils.getFirstStatementInBlock(tryBlock), variable)) {
        return true;
      }
    }
    else if (statement instanceof PsiIfStatement ifStatement) {
      final PsiExpression condition = ifStatement.getCondition();
      if (!(condition instanceof PsiBinaryExpression binaryExpression)) {
        return false;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.NE != tokenType) {
        return false;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return false;
      }
      if (PsiTypes.nullType().equals(lhs.getType())) {
        if (!(rhs instanceof PsiReferenceExpression referenceExpression)) {
          return false;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!variable.equals(target)) {
          return false;
        }
      }
      else if (PsiTypes.nullType().equals(rhs.getType())) {
        if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
          return false;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!variable.equals(target)) {
          return false;
        }
      }
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      return isResourceClose(thenBranch, variable);
    }
    else if (statement instanceof PsiBlockStatement blockStatement) {
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      return isResourceClose(ControlFlowUtils.getFirstStatementInBlock(codeBlock), variable);
    }
    return false;
  }

  protected boolean isResourceClose(PsiMethodCallExpression call, PsiVariable resource) {
    return MethodCallUtils.isMethodCallOnVariable(call, resource, HardcodedMethodConstants.CLOSE);
  }

  @ApiStatus.Internal
  public boolean isResourceEscaping(@Nullable PsiVariable boundVariable, @NotNull PsiExpression resourceCreationExpression) {
    if (boundVariable instanceof PsiField) return true;
    if (isSystemErrOrOutUse(resourceCreationExpression)) {
      return true;
    }
    if (resourcePotentiallyCreatedFromOther(resourceCreationExpression)) return true;
    final PsiElement parent = ExpressionUtils.getPassThroughParent(resourceCreationExpression);
    if (parent instanceof PsiLambdaExpression lambda) {
      PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
      if (!PsiTypes.voidType().equals(returnType)) {
        return true;
      }
    }
    if (parent instanceof PsiReturnStatement) {
      return true;
    }
    if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      if (PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression()) != resourceCreationExpression) {
        return true; // non-sensical code
      }
      if (assignedToField(assignmentExpression)) {
        return true;
      }
    }
    else if (parent instanceof PsiExpressionList) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiAnonymousClass) {
        grandParent = grandParent.getParent();
      }
      if (grandParent instanceof PsiCallExpression) {
        return anyMethodMayClose || isResourceCreation((PsiExpression) grandParent);
      }
    }
    if (boundVariable == null) {
      return false;
    }
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(boundVariable, PsiCodeBlock.class, true, PsiMember.class);
    if (codeBlock == null) {
      return false;
    }
    final EscapeVisitor visitor = new EscapeVisitor(boundVariable, resourceCreationExpression);
    codeBlock.accept(visitor);
    return visitor.isEscaped();
  }

  private boolean resourcePotentiallyCreatedFromOther(@NotNull PsiExpression resourceCreationExpression) {
    if (resourceCreationExpression instanceof PsiCallExpression) {
      PsiExpressionList argumentList = ((PsiCallExpression)resourceCreationExpression).getArgumentList();
      if (argumentList != null && ContainerUtil.or(argumentList.getExpressions(), this::canTakeOwnership)) {
        // resource was created from other, potentially leaking resource
        return true;
      }
      if (resourceCreationExpression instanceof PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null && isResourceCreation(qualifier)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean assignedToField(PsiAssignmentExpression assignmentExpression) {
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
    if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
      return false;
    }
    final PsiElement target = referenceExpression.resolve();
    return target instanceof PsiField;
  }

  private static boolean isSystemErrOrOutUse(PsiExpression resourceCreationExpression) {
    if (!(resourceCreationExpression instanceof PsiMethodCallExpression methodCallExpression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiField field) {
        final String fieldName = field.getName();
        if ("out".equals(fieldName) || "err".equals(fieldName)) {
          final PsiClass containingClass = field.getContainingClass();
          if (containingClass != null && "java.lang.System".equals(containingClass.getQualifiedName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private final class CloseVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean containsClose;
    private final PsiVariable resource;
    private final String resourceName;

    private CloseVisitor(PsiVariable resource) {
      this.resource = resource;
      this.resourceName = resource.getName();
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!containsClose) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression call) {
      if (containsClose) {
        return;
      }
      super.visitMethodCallExpression(call);
      if (!isResourceClose(call, resource)) {
        return;
      }
      containsClose = true;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
      // check if resource is closed in a method like IOUtils.silentClose()
      super.visitReferenceExpression(referenceExpression);
      if (containsClose) {
        return;
      }
      final String text = referenceExpression.getText();
      if (text == null || !text.equals(resourceName)) {
        return;
      }
      final PsiElement parent = referenceExpression.getParent();
      if (!(parent instanceof PsiExpressionList argumentList)) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (methodName == null || !methodName.contains(HardcodedMethodConstants.CLOSE)) {
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (target == null || !target.equals(resource)) {
        return;
      }
      // handle any call to a method with "close" in the name and
      // a resource argument inside the finally block as closing the resource.
      // like e.g. org.apache.commons.io.IOUtils.closeQuietly()
      // and com.google.common.io.Closeables.closeQuietly()
      containsClose = true;
    }

    private boolean containsClose() {
      return containsClose;
    }
  }

  private class EscapeVisitor extends JavaRecursiveElementWalkingVisitor {

    private final PsiVariable boundVariable;
    private final @Nullable PsiLoopStatement loopStatement;
    private boolean escaped;

    EscapeVisitor(@NotNull PsiVariable boundVariable, @NotNull PsiExpression creationExpression) {
      this.boundVariable = boundVariable;
      loopStatement = PsiTreeUtil.getParentOfType(creationExpression, PsiLoopStatement.class);
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}

    @Override
    public void visitClass(@NotNull PsiClass aClass) {}

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (escaped) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression value = PsiUtil.deparenthesizeExpression(statement.getReturnValue());
      if (!(value instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (boundVariable.equals(target)) {
        escaped = true;
      }
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression rhs = PsiUtil.deparenthesizeExpression(expression.getRExpression());
      if (!(rhs instanceof PsiReferenceExpression referenceExpression)) {
        return;
      }
      final PsiElement target = referenceExpression.resolve();
      if (!boundVariable.equals(target)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.deparenthesizeExpression(expression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression lReferenceExpression)) {
        return;
      }
      final PsiElement lTarget = lReferenceExpression.resolve();
      if (lTarget instanceof PsiField) {
        escaped = true;
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      // Case: variable.close()
      if (!ignoreResourcesWithClose) {
        return;
      }
      if (!ExpressionUtils.isReferenceTo(expression, boundVariable)) return;
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(expression);
      if (call == null) return;
      String callName = call.getMethodExpression().getReferenceName();
      if ("close".equals(callName) && !canBeCloseOutsideLoop(call)) {
        escaped = true;
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ignoreResourcesWithClose) {
        return;
      }
      String name = expression.getMethodExpression().getReferenceName();
      if (name == null) return;
      if ((StringUtil.containsIgnoreCase(name, "close") || StringUtil.containsIgnoreCase(name, "cleanup")) &&
          !canBeCloseOutsideLoop(expression)) {
        escaped = true;
      }
    }

    private boolean canBeCloseOutsideLoop(PsiExpression expression) {
      if (loopStatement == null) {
        return false;
      }
      return !PsiTreeUtil.isAncestor(loopStatement, expression, false);
    }

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression callExpression) {
      super.visitCallExpression(callExpression);
      if (!anyMethodMayClose) {
        return;
      }
      final PsiExpressionList argumentList = callExpression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      for (PsiExpression argument : expressions) {
        final PsiExpression maybeReferenceExpression = PsiUtil.deparenthesizeExpression(argument);
        if (!(maybeReferenceExpression instanceof PsiReferenceExpression)) {
          continue;
        }
        if (ExpressionUtils.isReferenceTo(maybeReferenceExpression, boundVariable)) {
          if (callExpression instanceof PsiMethodCallExpression) {
            PsiExpression returnedValue = JavaMethodContractUtil.findReturnedValue((PsiMethodCallExpression)callExpression);
            if (returnedValue != null && returnedValue == maybeReferenceExpression) return;
          }
          escaped = true;
          break;
        }
      }
    }

    @Override
    public void visitResourceVariable(@NotNull PsiResourceVariable variable) {
      super.visitResourceVariable(variable);
      if (ExpressionUtils.isReferenceTo(variable.getInitializer(), boundVariable)) {
        escaped = true;
      }
    }

    @Override
    public void visitResourceExpression(@NotNull PsiResourceExpression expression) {
      super.visitResourceExpression(expression);
      if (ExpressionUtils.isReferenceTo(expression.getExpression(), boundVariable)) {
        escaped = true;
      }
    }

    public boolean isEscaped() {
      return escaped;
    }
  }
}
