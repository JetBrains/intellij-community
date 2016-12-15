/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.streamToLoop;

import com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.StreamToLoopReplacementContext;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Tagir Valeev
 */
abstract class Operation {
  boolean changesVariable() {
    return false;
  }

  StreamEx<StreamToLoopInspection.OperationRecord> nestedOperations() {
    return StreamEx.empty();
  }

  void rename(String oldName, String newName, StreamToLoopReplacementContext context) {}

  abstract String wrap(StreamVariable inVar,
                       StreamVariable outVar,
                       String code,
                       StreamToLoopReplacementContext context);

  Operation combineWithNext(Operation next) {
    return null;
  }

  public void registerUsedNames(Consumer<String> usedNameConsumer) {}

  public void suggestNames(StreamVariable inVar, StreamVariable outVar) {}

  @Nullable
  static Operation createIntermediate(@NotNull String name, @NotNull PsiExpression[] args,
                                      @NotNull StreamVariable outVar, @NotNull PsiType inType) {
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
    if(name.equals("peek") && args.length == 1) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new PeekOperation(fn);
    }
    if((name.equals("boxed") || name.equals("asLongStream") || name.equals("asDoubleStream")) && args.length == 0) {
      return new WideningOperation();
    }
    if ((name.equals("flatMap") || name.equals("flatMapToInt") || name.equals("flatMapToLong") || name.equals("flatMapToDouble")) &&
        args.length == 1) {
      return FlatMapOperation.from(outVar, args[0], inType);
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
    public void registerUsedNames(Consumer<String> usedNameConsumer) {
      myFn.registerUsedNames(usedNameConsumer);
    }

    @Override
    final String wrap(StreamVariable inVar, StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      myFn.transform(context, inVar.getName());
      return wrap(outVar, code, context);
    }

    abstract String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context);

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myFn.rename(oldName, newName, context);
    }

    @Override
    public void suggestNames(StreamVariable inVar, StreamVariable outVar) {
      myFn.suggestVariableName(inVar, 0);
    }
  }

  static class FilterOperation extends LambdaIntermediateOperation {
    public FilterOperation(FunctionHelper fn) {
      super(fn);
    }

    @Override
    String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      return "if(" + myFn.getText() + ") {\n" + code + "}\n";
    }
  }

  static class PeekOperation extends LambdaIntermediateOperation {
    public PeekOperation(FunctionHelper fn) {
      super(fn);
    }

    @Override
    String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      return myFn.getText() + ";\n" + code;
    }
  }

  static class MapOperation extends LambdaIntermediateOperation {
    public MapOperation(FunctionHelper fn) {
      super(fn);
    }

    @Override
    public void suggestNames(StreamVariable inVar, StreamVariable outVar) {
      super.suggestNames(inVar, outVar);
      myFn.suggestOutputNames(outVar);
    }

    @Override
    String wrap(StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      return outVar.getDeclaration() + " = " + myFn.getText() + ";\n" + code;
    }

    @Override
    boolean changesVariable() {
      return true;
    }
  }

  static class WideningOperation extends Operation {
    @Override
    String wrap(StreamVariable inVar,
                StreamVariable outVar,
                String code,
                StreamToLoopReplacementContext context) {
      return outVar.getDeclaration() + " = " + inVar + ";\n" + code;
    }

    @Override
    boolean changesVariable() {
      return true;
    }
  }

  static class FlatMapOperation extends Operation {
    private String myVarName;
    private final FunctionHelper myFn;
    private final List<StreamToLoopInspection.OperationRecord> myRecords;

    private FlatMapOperation(String varName, FunctionHelper fn, List<StreamToLoopInspection.OperationRecord> records) {
      myVarName = varName;
      myFn = fn;
      myRecords = records;
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
    public void suggestNames(StreamVariable inVar, StreamVariable outVar) {
      myFn.suggestVariableName(inVar, 0);
    }

    @Override
    public void registerUsedNames(Consumer<String> usedNameConsumer) {
      myRecords.forEach(or -> or.myOperation.registerUsedNames(usedNameConsumer));
    }

    @Override
    void rename(String oldName, String newName, StreamToLoopReplacementContext context) {
      myRecords.forEach(or -> or.myOperation.rename(oldName, newName, context));
    }

    @Override
    String wrap(StreamVariable inVar, StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      if(!myVarName.equals(inVar.getName())) {
        rename(myVarName, inVar.getName(), context);
      }
      StreamToLoopReplacementContext innerContext = new StreamToLoopReplacementContext(context, myRecords);
      String replacement = code;
      for(StreamToLoopInspection.OperationRecord or : StreamEx.ofReversed(myRecords)) {
        replacement = or.myOperation.wrap(or.myInVar, or.myOutVar, replacement, innerContext);
      }
      return StreamEx.of(innerContext.getDeclarations()).map(str -> str  + "\n").joining()+replacement;
    }

    @Nullable
    public static FlatMapOperation from(StreamVariable outVar, PsiExpression arg, PsiType inType) {
      FunctionHelper fn = FunctionHelper.create(arg, 1);
      if(fn == null) return null;
      String varName = fn.tryLightTransform(inType);
      if(varName == null) return null;
      PsiExpression body = fn.getExpression();
      if(!(body instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression terminalCall = (PsiMethodCallExpression)body;
      List<StreamToLoopInspection.OperationRecord> records = StreamToLoopInspection.extractOperations(outVar, terminalCall);
      if(records == null || StreamToLoopInspection.getTerminal(records) != null) return null;
      return new FlatMapOperation(varName, fn, records);
    }
  }

  static class DistinctOperation extends Operation {
    @Override
    String wrap(StreamVariable inVar, StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      String set =
        context.declare("uniqueValues", "java.util.Set<" + PsiTypesUtil.boxIfPossible(inVar.getType()) + ">", "new java.util.HashSet<>()");
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
    public void registerUsedNames(Consumer<String> usedNameConsumer) {
      FunctionHelper.processUsedNames(myExpression, usedNameConsumer);
    }

    @Override
    String wrap(StreamVariable inVar, StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
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
    public void registerUsedNames(Consumer<String> usedNameConsumer) {
      FunctionHelper.processUsedNames(myLimit, usedNameConsumer);
    }

    @Override
    String wrap(StreamVariable inVar, StreamVariable outVar, String code, StreamToLoopReplacementContext context) {
      String limit = context.declare("limit", "long", myLimit.getText());
      return "if(" + limit + "--==0) " + context.getBreakStatement() + code;
    }
  }
}
