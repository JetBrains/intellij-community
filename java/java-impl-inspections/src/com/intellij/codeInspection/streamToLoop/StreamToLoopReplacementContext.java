// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamToLoop;

import com.intellij.codeInspection.streamToLoop.Operation.FlatMapOperation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.OperationRecord;
import static com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.ResultKind;

class StreamToLoopReplacementContext extends ChainContext {
  private final boolean myHasNestedLoops;
  private final Set<String> myUsedLabels;
  private final CommentTracker myCommentTracker;
  private final String mySuffix;
  private String myFinisher;
  private String myLabel;

  private static final Logger LOG = Logger.getInstance(StreamToLoopReplacementContext.class);

  StreamToLoopReplacementContext(PsiStatement statement,
                                 List<OperationRecord> records,
                                 @NotNull PsiExpression streamExpression,
                                 CommentTracker ct) {
    super(streamExpression);
    myHasNestedLoops = ContainerUtil.exists(records, or -> or.myOperation instanceof FlatMapOperation);
    mySuffix = myHasNestedLoops ? "Outer" : "";
    myUsedLabels = StreamEx.iterate(statement, Objects::nonNull, PsiElement::getParent).select(PsiLabeledStatement.class)
      .map(PsiLabeledStatement::getName).toSet();
    myCommentTracker = ct;
  }

  StreamToLoopReplacementContext(StreamToLoopReplacementContext parentContext, List<OperationRecord> records) {
    super(parentContext);
    myUsedLabels = parentContext.myUsedLabels;
    mySuffix = "Inner";
    myHasNestedLoops = ContainerUtil.exists(records, or -> or.myOperation instanceof FlatMapOperation);
    myCommentTracker = parentContext.myCommentTracker;
  }

  public void registerReusedElement(@Nullable PsiElement element) {
    if (element == null) return;
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitVariable(@NotNull PsiVariable variable) {
        super.visitVariable(variable);
        myUsedNames.add(variable.getName());
      }
    });
    myCommentTracker.markUnchanged(element);
  }

  private @Nullable String allocateLabel() {
    if (!myHasNestedLoops) return null;
    if (myLabel == null) {
      String base = StringUtil.toUpperCase(mySuffix);
      myLabel = IntStreamEx.ints().mapToObj(i -> i == 0 ? base : base + i)
        .remove(myUsedLabels::contains).findFirst().orElseThrow(IllegalArgumentException::new);
      myUsedLabels.add(myLabel);
    }
    return myLabel;
  }

  public String getLoopLabel() {
    return myLabel == null ? "" : myLabel + ":\n";
  }

  public String getBreakStatement() {
    String label = allocateLabel();
    return label == null ? "break;\n" : "break " + label + ";\n";
  }

  @Override
  public String declare(String desiredName, String type, String initializer) {
    String name = registerVarName(
      mySuffix.isEmpty() ? Collections.singleton(desiredName) : Arrays.asList(desiredName, desiredName + mySuffix));
    addBeforeStep(type + " " + name + " = " + initializer + ";");
    return name;
  }

  public String declareResult(String desiredName, PsiType type, String initializer, @NotNull ResultKind kind) {
    return declareResult(desiredName, type, null, initializer, kind);
  }

  public String declareResult(String desiredName,
                              PsiType type,
                              String mostAbstractAllowedType,
                              String initializer,
                              @NotNull ResultKind kind) {
    if (kind != ResultKind.UNKNOWN &&
        myChainExpression.getParent() instanceof PsiVariable var &&
        isCompatibleType(var, type, mostAbstractAllowedType) &&
        var.getParent() instanceof PsiDeclarationStatement declaration &&
        (kind == ResultKind.FINAL || VariableAccessUtils.canUseAsNonFinal(ObjectUtils.tryCast(var, PsiLocalVariable.class))) &&
        declaration.getDeclaredElements().length == 1) {
      myChainExpression = declaration;
      PsiVariable copy = (PsiVariable)var.copy();
      if (kind == ResultKind.NON_FINAL) {
        PsiModifierList modifierList = copy.getModifierList();
        if (modifierList != null) {
          modifierList.setModifierProperty(PsiModifier.FINAL, false);
        }
      }
      PsiExpression oldInitializer = copy.getInitializer();
      LOG.assertTrue(oldInitializer != null);
      oldInitializer.replace(createExpression(initializer));
      addBeforeStep(copy.getText());
      return var.getName();
    }
    String name = registerVarName(Arrays.asList(desiredName, "result"));
    addBeforeStep(type.getCanonicalText() + " " + name + " = " + initializer + ";");
    if (myFinisher != null) {
      throw new IllegalStateException("Finisher is already defined");
    }
    setFinisher(name);
    return name;
  }

  public boolean tryUnwrapOrElse(@NotNull Number wantedValue) {
    if (!(myChainExpression instanceof PsiExpression)) return false;
    PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier((PsiExpression)myChainExpression);
    if (call == null ||
        call.getParent() instanceof PsiExpressionStatement ||
        !"orElse".equals(call.getMethodExpression().getReferenceName())) {
      return false;
    }
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length == 1 && wantedValue.equals(ExpressionUtils.computeConstantExpression(args[0]))) {
      myChainExpression = call;
      return true;
    }
    return false;
  }

  private static boolean isCompatibleType(@NotNull PsiVariable var, @NotNull PsiType type, @Nullable String mostAbstractAllowedType) {
    if (EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(var.getType(), type)) return true;
    if (mostAbstractAllowedType == null) return false;
    PsiType[] superTypes = type.getSuperTypes();
    return ContainerUtil.exists(superTypes, superType -> InheritanceUtil.isInheritor(superType, mostAbstractAllowedType) &&
                                                           isCompatibleType(var, superType, mostAbstractAllowedType));
  }

  public PsiElement makeFinalReplacement() {
    LOG.assertTrue(myChainExpression != null);
    if (myFinisher == null || myChainExpression instanceof PsiStatement) {
      PsiElement toDelete = myChainExpression;
      if (toDelete instanceof PsiExpression && toDelete.getParent() instanceof PsiExpressionStatement) {
        toDelete = toDelete.getParent();
        while (toDelete instanceof PsiExpressionStatement && toDelete.getParent() instanceof PsiLabeledStatement) {
          toDelete = toDelete.getParent();
        }
      }
      myCommentTracker.delete(toDelete);
      return null;
    }
    else {
      PsiExpression expression = createExpression(myFinisher);
      PsiElement parent = myChainExpression.getParent();
      if (parent instanceof PsiExpression && ParenthesesUtils.areParenthesesNeeded(expression, (PsiExpression)parent, false)) {
        expression = createExpression("(" + myFinisher + ")");
      }
      return myCommentTracker.replace(myChainExpression, expression);
    }
  }

  public void setFinisher(String finisher) {
    myFinisher = finisher;
  }

  public void setFinisher(ConditionalExpression conditionalExpression) {
    if (conditionalExpression instanceof ConditionalExpression.Optional) {
      conditionalExpression = tryUnwrapOptional((ConditionalExpression.Optional)conditionalExpression, true);
    }
    setFinisher(conditionalExpression.asExpression());
  }

  public String assignAndBreak(ConditionalExpression conditionalExpression) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(myChainExpression, PsiStatement.class);
    boolean inReturn = statement instanceof PsiReturnStatement;
    if (conditionalExpression instanceof ConditionalExpression.Optional) {
      conditionalExpression = tryUnwrapOptional((ConditionalExpression.Optional)conditionalExpression, inReturn);
    }
    if (conditionalExpression instanceof ConditionalExpression.Boolean) {
      conditionalExpression = tryUnwrapBoolean((ConditionalExpression.Boolean)conditionalExpression, inReturn);
    }
    if (inReturn) {
      setFinisher(conditionalExpression.getFalseBranch());
      Object mark = new Object();
      PsiTreeUtil.mark(myChainExpression, mark);
      PsiElement returnCopy = statement.copy();
      PsiElement placeHolderCopy = PsiTreeUtil.releaseMark(returnCopy, mark);
      LOG.assertTrue(placeHolderCopy != null);
      PsiElement replacement = placeHolderCopy.replace(createExpression(conditionalExpression.getTrueBranch()));
      if (returnCopy == placeHolderCopy) {
        returnCopy = replacement;
      }
      String text = returnCopy.getText();
      if (returnCopy.getLastChild() instanceof PsiComment) {
        text += "\n";
      }
      return text;
    }
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(myChainExpression.getParent());
    if (parent instanceof PsiIfStatement ifStatement && ifStatement.getElseBranch() == null &&
        conditionalExpression instanceof ConditionalExpression.Boolean boolCondition &&
        !boolCondition.isInverted()) {
      PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      if (thenStatement instanceof PsiReturnStatement || thenStatement instanceof PsiThrowStatement) {
        myChainExpression = parent;
        return thenStatement.getText();
      }
      if (thenStatement instanceof PsiExpressionStatement) {
        myChainExpression = parent;
        return thenStatement.getText() + "\n" + getBreakStatement();
      }
    }
    if (conditionalExpression instanceof ConditionalExpression.Optional && myChainExpression instanceof PsiExpression) {
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier((PsiExpression)myChainExpression);
      if (call != null && call.getParent() instanceof PsiExpressionStatement) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 1 && "ifPresent".equals(call.getMethodExpression().getReferenceName())) {
          FunctionHelper fn = FunctionHelper.create(args[0], 1);
          if (fn != null) {
            fn.transform(this, ((ConditionalExpression.Optional)conditionalExpression).unwrap("").getTrueBranch());
            myChainExpression = call.getParent();
            return fn.getStatementText() + getBreakStatement();
          }
        }
      }
    }
    String found =
      declareResult(conditionalExpression.getCondition(), createType(conditionalExpression.getType()),
                    conditionalExpression.getFalseBranch(), ResultKind.NON_FINAL);
    return found + " = " + conditionalExpression.getTrueBranch() + ";\n" + getBreakStatement();
  }

  private ConditionalExpression tryUnwrapBoolean(ConditionalExpression.Boolean condition, boolean unwrapLazilyEvaluated) {
    if (myChainExpression instanceof PsiExpression) {
      PsiExpression negation = BoolUtils.findNegation((PsiExpression)myChainExpression);
      if (negation != null) {
        myChainExpression = negation;
        condition = condition.negate();
      }

      PsiElement parent = PsiUtil.skipParenthesizedExprUp(myChainExpression.getParent());
      ConditionalExpression candidate = null;
      if (parent instanceof PsiPolyadicExpression expression) {
        PsiExpression[] operands = expression.getOperands();
        if (operands.length > 1 && PsiTreeUtil.isAncestor(operands[0], myChainExpression, false)) {
          IElementType type = expression.getOperationTokenType();
          if (type.equals(JavaTokenType.ANDAND)) {
            candidate = condition
              .toPlain(PsiTypes.booleanType(), StreamEx.of(operands, 1, operands.length).map(PsiExpression::getText).joining(" && "), "false");
          }
          else if (type.equals(JavaTokenType.OROR)) {
            candidate = condition
              .toPlain(PsiTypes.booleanType(), "true", StreamEx.of(operands, 1, operands.length).map(PsiExpression::getText).joining(" || "));
          }
        }
      }
      else if (parent instanceof PsiConditionalExpression ternary &&
               PsiTreeUtil.isAncestor(ternary.getCondition(), myChainExpression, false)) {
        PsiType type = ternary.getType();
        PsiExpression thenExpression = ternary.getThenExpression();
        PsiExpression elseExpression = ternary.getElseExpression();
        if (type != null && thenExpression != null && elseExpression != null) {
          candidate = condition.toPlain(type, thenExpression.getText(), elseExpression.getText());
        }
      }
      if (candidate != null &&
          (unwrapLazilyEvaluated || ExpressionUtils.isSafelyRecomputableExpression(createExpression(candidate.getFalseBranch())))) {
        myChainExpression = parent;
        return candidate;
      }
    }
    return condition;
  }

  private @NotNull ConditionalExpression tryUnwrapOptional(ConditionalExpression.Optional condition, boolean unwrapLazilyEvaluated) {
    if (myChainExpression instanceof PsiExpression) {
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier((PsiExpression)myChainExpression);
      if (call != null && !(call.getParent() instanceof PsiExpressionStatement)) {
        String name = call.getMethodExpression().getReferenceName();
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0 && "isPresent".equals(name)) {
          myChainExpression = call;
          return new ConditionalExpression.Boolean(condition.getCondition(), false);
        }
        if (args.length == 1) {
          String absentExpression = null;
          if ("orElse".equals(name)) {
            absentExpression = args[0].getText();
          }
          else if (unwrapLazilyEvaluated && "orElseGet".equals(name)) {
            FunctionHelper helper = FunctionHelper.create(args[0], 0);
            if (helper != null) {
              helper.transform(this);
              absentExpression = helper.getText();
            }
          }
          if (absentExpression != null) {
            myChainExpression = call;
            return condition.unwrap(absentExpression);
          }
        }
      }
    }
    return condition;
  }
}
