// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.optionalToIf;

import com.intellij.codeInspection.optionalToIf.OptionalToIfInspection.OperationRecord;
import com.intellij.codeInspection.streamToLoop.ChainVariable;
import com.intellij.codeInspection.streamToLoop.FunctionHelper;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

abstract class IntermediateOperation implements Operation {

  @Nullable
  static IntermediateOperation create(@NotNull String name, @NotNull PsiExpression[] args) {
    if (args.length != 1) return null;

    if (name.equals("map")) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new Map(fn);
    }
    if (name.equals("filter")) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : new Filter(fn);
    }
    if (name.equals("or")) {
      FunctionHelper fn = FunctionHelper.create(args[0], 0);
      return fn == null ? null : Or.create(fn);
    }
    if (name.equals("flatMap")) {
      FunctionHelper fn = FunctionHelper.create(args[0], 1);
      return fn == null ? null : FlatMap.create(fn);
    }

    return null;
  }

  static class Filter extends IntermediateOperation {

    private final FunctionHelper myFn;

    @Contract(pure = true)
    Filter(FunctionHelper fn) {
      myFn = fn;
    }

    @NotNull
    @Override
    public ChainVariable getOutVar(@NotNull ChainVariable inVar) {
      return inVar;
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName, @NotNull OptionalToIfContext context) {
      myFn.rename(oldName, newName, context);
    }

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      myFn.preprocessVariable(context, inVar, 0);
    }

    @Nullable
    @Override
    public String generate(@NotNull ChainVariable inVar,
                           @NotNull ChainVariable outVar,
                           @NotNull String code,
                           @NotNull OptionalToIfContext context) {
      myFn.transform(context, outVar.getName());
      return context.generateCondition(myFn.getExpression(), code);
    }
  }

  static class Map extends IntermediateOperation {

    private final FunctionHelper myFn;

    @Contract(pure = true)
    Map(FunctionHelper fn) {
      myFn = fn;
    }

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      myFn.preprocessVariable(context, inVar, 0);
      myFn.suggestOutputNames(context, outVar);
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName, @NotNull OptionalToIfContext context) {
      myFn.rename(oldName, newName, context);
    }

    @NotNull
    @Override
    public ChainVariable getOutVar(@NotNull ChainVariable inVar) {
      return new ChainVariable(myFn.getResultType());
    }

    @Nullable
    @Override
    public String generate(@NotNull ChainVariable inVar,
                           @NotNull ChainVariable outVar,
                           @NotNull String code,
                           @NotNull OptionalToIfContext context) {
      myFn.transform(context, inVar.getName());
      return outVar.getDeclaration(myFn.getText()) +
             context.generateNotNullCondition(outVar.getName(), code);
    }
  }

  static class Or extends IntermediateOperation {

    private final List<OperationRecord> myRecords;

    @Contract(pure = true)
    Or(List<OperationRecord> records) {
      myRecords = records;
    }

    @NotNull
    @Override
    public ChainVariable getOutVar(@NotNull ChainVariable inVar) {
      return new ChainVariable(inVar.getType());
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName, @NotNull OptionalToIfContext context) {
      myRecords.forEach(r -> r.myOperation.rename(oldName, newName, context));
    }

    @NotNull
    @Override
    public StreamEx<OperationRecord> nestedOperations() {
      return StreamEx.of(myRecords).flatMap(or -> StreamEx.of(or).append(or.myOperation.nestedOperations()));
    }

    @Nullable
    static Or create(@NotNull FunctionHelper fn) {
      List<OperationRecord> records = extractRecords(fn);
      return records == null ? null : new Or(records);
    }

    @Nullable
    @Override
    public String generate(@NotNull ChainVariable inVar,
                           @NotNull ChainVariable outVar,
                           @NotNull String code,
                           @NotNull OptionalToIfContext context) {
      String orResult = myRecords.get(myRecords.size() - 1).myOutVar.getName();
      String orCode = OptionalToIfInspection.wrapCode(context, myRecords, outVar.getName() + " = " + orResult + ";");
      if (orCode == null) return null;
      return "if (" + outVar.getName() + " == null) {\n" +
             orCode +
             "\n}" +
             context.generateNotNullCondition(outVar.getName(), code);
    }
  }

  static class FlatMap extends IntermediateOperation {

    private final List<OperationRecord> myRecords;
    private final String myVarName;
    private final FunctionHelper myFn;

    @Contract(pure = true)
    private FlatMap(List<OperationRecord> records, String varName, FunctionHelper fn) {
      myRecords = records;
      myVarName = varName;
      myFn = fn;
    }

    @NotNull
    @Override
    public ChainVariable getOutVar(@NotNull ChainVariable inVar) {
      ChainVariable outVar = myRecords.get(myRecords.size() - 1).myOutVar;
      return myVarName.equals(outVar.getName()) ? inVar : outVar;
    }

    @Nullable
    static FlatMap create(@NotNull FunctionHelper fn) {
      String varName = fn.tryLightTransform();
      if (varName == null) return null;
      List<OperationRecord> records = extractRecords(fn);
      return records == null ? null : new FlatMap(records, varName, fn);
    }

    @NotNull
    @Override
    public StreamEx<OperationRecord> nestedOperations() {
      return StreamEx.of(myRecords).flatMap(or -> StreamEx.of(or).append(or.myOperation.nestedOperations()));
    }

    @Nullable
    @Override
    public String generate(@NotNull ChainVariable inVar,
                           @NotNull ChainVariable outVar,
                           @NotNull String code,
                           @NotNull OptionalToIfContext context) {
      List<OperationRecord> records = StreamEx.of(myRecords).map(r -> replaceFnVariable(r, inVar, context)).collect(Collectors.toList());
      return OptionalToIfInspection.wrapCode(context, records, code);
    }

    @NotNull
    @Contract("_, _, _ -> new")
    private OperationRecord replaceFnVariable(@NotNull OperationRecord record,
                                              @NotNull ChainVariable outerVar,
                                              @NotNull OptionalToIfContext context) {
      ChainVariable inVar = replaceFnVariable(record.myInVar, outerVar);
      ChainVariable outVar = replaceFnVariable(record.myOutVar, outerVar);
      Operation operation = record.myOperation;
      operation.rename(myVarName, outerVar.getName(), context);
      return new OperationRecord(inVar, outVar, operation);
    }

    private ChainVariable replaceFnVariable(@NotNull ChainVariable variable, @NotNull ChainVariable replacement) {
      return myVarName.equals(variable.getName()) ? replacement : variable;
    }

    @Override
    public void rename(@NotNull String oldName, @NotNull String newName, @NotNull OptionalToIfContext context) {
      myRecords.forEach(r -> r.myOperation.rename(oldName, newName, context));
    }

    @Override
    public void preprocessVariables(@NotNull ChainVariable inVar, @NotNull ChainVariable outVar, @NotNull OptionalToIfContext context) {
      String name = myFn.getParameterName(0);
      if (name != null) {
        inVar.addBestNameCandidate(name);
      }
    }
  }

  @Nullable
  private static List<OperationRecord> extractRecords(@NotNull FunctionHelper fn) {
    PsiMethodCallExpression chainExpression = tryCast(fn.getExpression(), PsiMethodCallExpression.class);
    if (chainExpression == null) return null;
    List<Operation> operations = OptionalToIfInspection.extractOperations(chainExpression, false);
    if (operations == null || operations.isEmpty()) return null;
    return OptionalToIfInspection.createRecords(operations);
  }
}
