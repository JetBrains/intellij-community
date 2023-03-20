// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamToLoop;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.StreamApiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

abstract class Operation {
  boolean changesVariable() {
    return false;
  }

  StreamEx<StreamToLoopInspection.OperationRecord> nestedOperations() {
    return StreamEx.empty();
  }

  void rename(String oldName, String newName, StreamToLoopReplacementContext context) {}

  abstract String wrap(ChainVariable inVar,
                       ChainVariable outVar,
                       String code,
                       StreamToLoopReplacementContext context);

  Operation combineWithNext(Operation next) {
    return null;
  }

  public void registerReusedElements(Consumer<? super PsiElement> consumer) {}

  public void preprocessVariables(StreamToLoopReplacementContext context, ChainVariable inVar, ChainVariable outVar) {}

  @Nullable
  static Operation createIntermediate(@NotNull String name, PsiExpression @NotNull [] args,
                                      @NotNull ChainVariable outVar, @NotNull PsiType inType, boolean supportUnknownSources) {
    if(name.equals("distinct") && args.length == 0) {
      return new DistinctOperation();
    }
    if(name.equals("skip") && args.length == 1) {
      return new SkipOperation(args[0]);
    }
    if(name.equals("limit") && args.length == 1) {
      return new LimitOperation(args[0]);
    }
    if(name.equals("filter") && args.length == 1) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new FilterOperation(fn);
    }
    if(name.equals("takeWhile") && args.length == 1) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new TakeWhileOperation(fn);
    }
    if(name.equals("dropWhile") && args.length == 1) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new DropWhileOperation(fn);
    }
    if (name.equals("nonNull") && args.length == 0) { // StreamEx
      return new FilterOperation(new FunctionHelper.InlinedFunctionHelper(PsiTypes.booleanType(), 1, "{0} != null"));
    }
    if(name.equals("sorted") && !(inType instanceof PsiPrimitiveType)) {
      return new SortedOperation(args.length == 1 ? args[0] : null);
    }
    if(name.equals("peek") && args.length == 1) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new PeekOperation(fn);
    }
    if((name.equals("boxed") || name.equals("asLongStream") || name.equals("asDoubleStream")) && args.length == 0) {
      return new WideningOperation();
    }
    if ((name.equals("flatMap") || name.equals("flatMapToInt") || name.equals("flatMapToLong") || name.equals("flatMapToDouble")) &&
        args.length == 1) {
      return FlatMapOperation.from(outVar, args[0], supportUnknownSources);
    }
    if ((name.equals("map") ||
         name.equals("mapToInt") ||
         name.equals("mapToLong") ||
         name.equals("mapToDouble") ||
         name.equals("mapToObj")) && args.length == 1) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new MapOperation(fn);
    }
    return null;
  }

  static abstract class LambdaIntermediateOperation extends Operation {
    final FunctionHelper myFn;

    LambdaIntermediateOperation(FunctionHelper fn) {
      myFn = fn;
    }

    @Override
    public void registerReusedElements(Consumer<? super PsiElement> consumer) {
      myFn.registerReusedElements(consumer);
    }

    @Override
    final String wrap(ChainVariable inVar, ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      myFn.transform(context, inVar.getName());
      return wrap(outVar, code, context);
    }

    abstract String wrap(ChainVariable outVar, String code, StreamToLoopReplacementContext context);

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myFn.rename(oldName, newName, context);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, ChainVariable inVar, ChainVariable outVar) {
      myFn.preprocessVariable(context, inVar, 0);
    }
  }

  static class FilterOperation extends LambdaIntermediateOperation {
    FilterOperation(FunctionHelper fn) {
      super(fn);
    }

    @Override
    String wrap(ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      return "if(" + myFn.getText() + ") {\n" + code + "}\n";
    }
  }

  static class TakeWhileOperation extends LambdaIntermediateOperation {
    TakeWhileOperation(FunctionHelper fn) {
      super(fn);
    }

    @Override
    String wrap(ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      return "if(" + BoolUtils.getNegatedExpressionText(myFn.getExpression()) + ") {\n" +
             context.getBreakStatement() + "}\n" + code;
    }
  }

  static class DropWhileOperation extends LambdaIntermediateOperation {
    DropWhileOperation(FunctionHelper fn) {
      super(fn);
    }

    @Override
    String wrap(ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      String dropping = context.declare("dropping", "boolean", "true");
      return "if(" + dropping + ") {\n" +
             "if(" + myFn.getText() + ") {\ncontinue;\n}\n" +
             dropping + "=false;\n" +
             "}\n" + code;
    }
  }

  static class PeekOperation extends LambdaIntermediateOperation {
    PeekOperation(FunctionHelper fn) {
      super(fn);
    }

    @Override
    String wrap(ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      return myFn.getStatementText() + code;
    }
  }

  static class MapOperation extends LambdaIntermediateOperation {
    MapOperation(FunctionHelper fn) {
      super(fn);
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, ChainVariable inVar, ChainVariable outVar) {
      super.preprocessVariables(context, inVar, outVar);
      myFn.suggestOutputNames(context, outVar);
    }

    @Override
    String wrap(ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      return outVar.getDeclaration(myFn.getText()) + code;
    }

    @Override
    boolean changesVariable() {
      return true;
    }
  }

  static class WideningOperation extends Operation {
    @Override
    String wrap(ChainVariable inVar,
                ChainVariable outVar,
                String code,
                StreamToLoopReplacementContext context) {
      return outVar.getDeclaration(inVar.getName()) + code;
    }

    @Override
    boolean changesVariable() {
      return true;
    }
  }

  static final class FlatMapOperation extends Operation {
    private final String myVarName;
    private final FunctionHelper myFn;
    private final List<StreamToLoopInspection.OperationRecord> myRecords;
    private PsiExpression myCondition;
    private final boolean myInverted;

    private FlatMapOperation(String varName,
                             FunctionHelper fn,
                             List<StreamToLoopInspection.OperationRecord> records,
                             PsiExpression condition, boolean inverted) {
      myVarName = varName;
      myFn = fn;
      myRecords = records;
      myCondition = condition;
      myInverted = inverted;
    }

    @Override
    StreamEx<StreamToLoopInspection.OperationRecord> nestedOperations() {
      return StreamEx.of(myRecords).flatMap(or -> StreamEx.of(or).append(or.myOperation.nestedOperations()));
    }

    @Override
    boolean changesVariable() {
      return true;
    }

    @Override
    public void preprocessVariables(StreamToLoopReplacementContext context, ChainVariable inVar, ChainVariable outVar) {
      String name = myFn.getParameterName(0);
      if (name != null) {
        inVar.addBestNameCandidate(name);
      }
    }

    @Override
    public void registerReusedElements(Consumer<? super PsiElement> consumer) {
      myRecords.forEach(or -> or.myOperation.registerReusedElements(consumer));
      if(myCondition != null) {
        consumer.accept(myCondition);
      }
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myRecords.forEach(or -> or.myOperation.rename(oldName, newName, context));
      if (myCondition != null) {
        myCondition = FunctionHelper.replaceVarReference(myCondition, oldName, newName, context);
      }
    }

    @Override
    String wrap(ChainVariable inVar, ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      if(!myVarName.equals(inVar.getName())) {
        rename(myVarName, inVar.getName(), context);
      }
      StreamToLoopReplacementContext innerContext = new StreamToLoopReplacementContext(context, myRecords);
      String replacement = code;
      for(StreamToLoopInspection.OperationRecord or : StreamEx.ofReversed(myRecords)) {
        replacement = or.myOperation.wrap(or.myInVar, or.myOutVar, replacement, innerContext);
      }
      if (myCondition != null) {
        String conditionText = myCondition.getText();
        if (myInverted) {
          conditionText = BoolUtils.getNegatedExpressionText(myCondition);
        }
        return "if(" + conditionText + "){\n" + replacement + "}\n";
      }
      return replacement;
    }

    @Nullable
    public static FlatMapOperation from(ChainVariable outVar, PsiExpression arg, boolean supportUnknownSources) {
      FunctionHelper fn = FunctionHelper.create(arg, 1);
      if(fn == null) return null;
      String varName = fn.tryLightTransform();
      if(varName == null) return null;
      PsiExpression body = PsiUtil.skipParenthesizedExprDown(fn.getExpression());
      PsiExpression condition = null;
      boolean inverted = false;
      if(body instanceof PsiConditionalExpression ternary) {
        condition = ternary.getCondition();
        PsiExpression thenExpression = PsiUtil.skipParenthesizedExprDown(ternary.getThenExpression());
        PsiExpression elseExpression = PsiUtil.skipParenthesizedExprDown(ternary.getElseExpression());
        if(StreamApiUtil.isNullOrEmptyStream(thenExpression)) {
          body = elseExpression;
          inverted = true;
        }
        else if(StreamApiUtil.isNullOrEmptyStream(elseExpression)) {
          body = thenExpression;
        }
        else return null;
      }
      if(!(body instanceof PsiMethodCallExpression terminalCall)) return null;
      List<StreamToLoopInspection.OperationRecord> records =
        StreamToLoopInspection.extractOperations(outVar, terminalCall, supportUnknownSources);
      if(records == null || StreamToLoopInspection.getTerminal(records) != null) return null;
      return new FlatMapOperation(varName, fn, records, condition, inverted);
    }
  }

  static class DistinctOperation extends Operation {
    @Override
    String wrap(ChainVariable inVar, ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      String set =
        context.declare("uniqueValues", "java.util.Set<" + PsiTypesUtil.boxIfPossible(inVar.getType().getCanonicalText()) + ">",
                        "new java.util.HashSet<>()");
      return "if(" + set + ".add(" + inVar + ")) {\n" + code + "}\n";
    }
  }

  static class SkipOperation extends Operation {
    PsiExpression myExpression;

    SkipOperation(PsiExpression expression) {
      myExpression = expression;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myExpression = FunctionHelper.replaceVarReference(myExpression, oldName, newName, context);
    }

    @Override
    public void registerReusedElements(Consumer<? super PsiElement> consumer) {
      consumer.accept(myExpression);
    }

    @Override
    String wrap(ChainVariable inVar, ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      if (ExpressionUtils.isLiteral(myExpression, 1)) {
        String first = context.declare("first", "boolean", "true");
        return "if(" + first + ") {\n" + first + "=false;\ncontinue;\n}\n" + code;
      }
      String toSkip = context.declare("toSkip", "long", myExpression.getText());
      return "if(" + toSkip + ">0) {\n" + toSkip + "--;\ncontinue;\n}\n" + code;
    }
  }

  static class LimitOperation extends Operation {
    PsiExpression myLimit;

    LimitOperation(PsiExpression expression) {
      myLimit = expression;
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myLimit = FunctionHelper.replaceVarReference(myLimit, oldName, newName, context);
    }

    @Override
    public void registerReusedElements(Consumer<? super PsiElement> consumer) {
      consumer.accept(myLimit);
    }

    @Override
    String wrap(ChainVariable inVar, ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      String limit = context.declare("limit", "long", myLimit.getText());
      return "if(" + limit + "--==0) " + context.getBreakStatement() + code;
    }
  }

  static class SortedOperation extends Operation {
    private final @Nullable PsiExpression myComparator;

    SortedOperation(@Nullable PsiExpression comparator) {
      myComparator = comparator;
    }

    @Override
    Operation combineWithNext(Operation next) {
      if (next instanceof TerminalOperation.ToArrayTerminalOperation ||
          (next instanceof TerminalOperation.ToCollectionTerminalOperation
           && ((TerminalOperation.ToCollectionTerminalOperation)next).isList())) {
        return new TerminalOperation.SortedTerminalOperation((TerminalOperation.AccumulatedOperation)next, myComparator);
      }
      return null;
    }

    @Override
    String wrap(ChainVariable inVar, ChainVariable outVar, String code, StreamToLoopReplacementContext context) {
      String list = context.registerVarName(Arrays.asList("toSort", "listToSort"));
      context.addAfterStep(new SourceOperation.ForEachSource(context.createExpression(list)).wrap(null, outVar, code, context));
      context.addAfterStep(list + ".sort(" + (myComparator == null ? "null" : myComparator.getText()) + ");\n");
      String listType = CommonClassNames.JAVA_UTIL_LIST + "<" + inVar.getType().getCanonicalText() + ">";
      String initializer = "new " + CommonClassNames.JAVA_UTIL_ARRAY_LIST + "<>()";
      context.addBeforeStep(listType + " " + list + "=" + initializer + ";");
      return list+".add("+inVar+");\n";
    }
  }
}
