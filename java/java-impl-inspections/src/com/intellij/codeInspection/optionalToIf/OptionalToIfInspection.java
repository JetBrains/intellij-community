// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.optionalToIf;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.streamToLoop.ChainVariable;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public final class OptionalToIfInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final Set<String> SUPPORTED_TERMINALS = Set.of(
    "get", "orElse", "ifPresent", "orElseGet", "ifPresentOrElse", "isPresent", "isEmpty", "stream", "orElseThrow");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression terminalCall) {
        String methodName = terminalCall.getMethodExpression().getReferenceName();
        if (methodName == null || !SUPPORTED_TERMINALS.contains(methodName)) return;
        List<Operation> operations = extractOperations(terminalCall, true);
        if (operations == null || operations.isEmpty() || !(operations.get(0) instanceof SourceOperation)) return;
        OptionalToIfContext context = OptionalToIfContext.create(terminalCall);
        if (context == null) return;
        holder.registerProblem(terminalCall, JavaBundle.message("inspection.message.replace.optional.with.if.statements"), new ReplaceOptionalWithIfFix());
      }
    };
  }

  @Nullable
  static List<Operation> extractOperations(@NotNull PsiMethodCallExpression lastCall, boolean hasTerminalCall) {
    List<Operation> operations = new ArrayList<>();
    for (PsiMethodCallExpression call = lastCall; call != null; call = MethodCallUtils.getQualifierMethodCall(call)) {
      PsiMethod method = call.resolveMethod();
      if (method == null || !isOptionalMethod(method)) return null;
      PsiType type = call == lastCall && hasTerminalCall ? call.getType() : OptionalUtil.getOptionalElementType(call.getType());
      if (type == null) return null;
      String name = call.getMethodExpression().getReferenceName();
      if (name == null) return null;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      Operation operation = convertToOperation(name, type, args);
      if (operation == null) return null;
      operations.add(operation);
    }
    Collections.reverse(operations);
    return operations;
  }

  @Nullable
  private static Operation convertToOperation(@NotNull String name, @NotNull PsiType type, PsiExpression @NotNull [] args) {
    Operation operation = IntermediateOperation.create(name, args);
    if (operation != null) return operation;
    operation = TerminalOperation.create(name, args);
    if (operation != null) return operation;
    return SourceOperation.create(name, type, args);
  }

  private static boolean isOptionalMethod(@NotNull PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    return aClass != null && OptionalUtil.isJdkOptionalClassName(aClass.getQualifiedName());
  }

  private static StreamEx<OperationRecord> allOperations(List<OperationRecord> operations) {
    return StreamEx.of(operations).flatMap(or -> or.myOperation.nestedOperations().append(or));
  }

  @Nullable
  static String generateCode(@NotNull OptionalToIfContext context, @NotNull List<Operation> operations) {
    List<OperationRecord> records = createRecords(operations);

    allOperations(records).forEach(r -> r.myOperation.preprocessVariables(r.myInVar, r.myOutVar, context));
    allOperations(records).map(r -> r.myOutVar).distinct().filter(v -> !v.isRegistered()).forEach(v -> v.register(context));

    return wrapCode(context, records, "");
  }

  @Nullable
  static String wrapCode(@NotNull OptionalToIfContext context, @NotNull List<OperationRecord> records, @NotNull String code) {
    for (int i = records.size() - 1; i >= 0; i--) {
      OperationRecord record = records.get(i);
      Operation operation = record.myOperation;
      ChainVariable inVar = record.myInVar;
      ChainVariable outVar = record.myOutVar;
      code = operation.generate(inVar, outVar, code, context);
      if (code == null) return null;
      if (operation instanceof IntermediateOperation.Or) {
        context.addBeforeStep(outVar.getDeclaration("null"));
        context.setElseBranch(null);
        List<OperationRecord> rest = records.subList(0, i);
        String beforeCode = wrapCode(context, rest, outVar.getName() + "=" + inVar.getName() + ";");
        if (beforeCode == null) return null;
        return beforeCode + code;
      }
    }
    return code;
  }

  @NotNull
  static List<OperationRecord> createRecords(@NotNull List<Operation> operations) {
    ChainVariable inVar = ChainVariable.STUB;
    ChainVariable outVar;
    List<OperationRecord> records = new ArrayList<>(operations.size());
    for (Operation operation : operations) {
      outVar = operation.getOutVar(inVar);
      records.add(new OperationRecord(inVar, outVar, operation));
      inVar = outVar;
    }
    return records;
  }

  @Nullable
  static List<Instruction> createInstructions(PsiStatement @NotNull [] statements) {
    List<Instruction> instructions = new ArrayList<>(statements.length);
    for (PsiStatement statement : statements) {
      Instruction instruction = Instruction.create(statement);
      if (instruction == null) return null;
      instructions.add(instruction);
    }
    return instructions;
  }

  private static PsiStatement @NotNull [] addStatements(@NotNull PsiElementFactory factory,
                                                        @NotNull PsiStatement chainStatement,
                                                        @NotNull String code) {
    PsiStatement[] statements = ControlFlowUtils.unwrapBlock(factory.createStatementFromText("{\n" + code + "\n}", chainStatement));
    PsiElement parent = chainStatement.getParent();
    return ContainerUtil.map(statements, s -> (PsiStatement)parent.addBefore(s, chainStatement), PsiStatement.EMPTY_ARRAY);
  }

  static class OperationRecord {

    final Operation myOperation;
    ChainVariable myInVar;
    ChainVariable myOutVar;

    @Contract(pure = true)
    OperationRecord(ChainVariable inVar, ChainVariable outVar, Operation operation) {
      myInVar = inVar;
      myOutVar = outVar;
      myOperation = operation;
    }
  }

  private static class ReplaceOptionalWithIfFix extends PsiUpdateModCommandQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("quickfix.family.replace.optional.chain.with.if.statements");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiMethodCallExpression chainExpression = tryCast(element, PsiMethodCallExpression.class);
      if (chainExpression == null) return;
      PsiStatement chainStatement = PsiTreeUtil.getParentOfType(chainExpression, PsiStatement.class);
      if (chainStatement == null) return;
      List<Operation> operations = extractOperations(chainExpression, true);
      if (operations == null || operations.isEmpty()) return;
      OptionalToIfContext context = OptionalToIfContext.create(chainExpression);
      if (context == null) return;
      String code = generateCode(context, operations);
      if (code == null) return;
      code = context.addInitializer(code);
      PsiStatement firstStatement = chainStatement;
      PsiStatement[] statements = addStatements(factory, chainStatement, code);
      if (statements.length > 0) firstStatement = statements[0];
      List<Instruction> instructions = createInstructions(statements);
      if (instructions != null) {
        code = Simplifier.simplify(instructions);
        Arrays.stream(statements).forEach(PsiStatement::delete);
        statements = addStatements(factory, chainStatement, code);
        firstStatement = statements.length > 0 ? statements[0] : chainStatement;
      }
      CommentTracker tracker = new CommentTracker();
      tracker.grabComments(chainStatement);
      tracker.insertCommentsBefore(firstStatement);
      chainStatement.delete();
    }
  }
}
