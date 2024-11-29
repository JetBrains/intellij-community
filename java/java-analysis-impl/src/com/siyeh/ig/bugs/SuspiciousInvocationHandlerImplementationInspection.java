// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.GetterDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SuspiciousInvocationHandlerImplementationInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final String HANDLER_CLASS = "java.lang.reflect.InvocationHandler";
  private static final String[] HANDLER_ARGUMENT_TYPES =
    {CommonClassNames.JAVA_LANG_OBJECT, "java.lang.reflect.Method", CommonClassNames.JAVA_LANG_OBJECT + "[]"};
  private static final CallMatcher INVOKE = CallMatcher.instanceCall(HANDLER_CLASS, "invoke").parameterTypes(HANDLER_ARGUMENT_TYPES);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        if (INVOKE.methodMatches(method)) {
          check(method);
        }
        if (method.getReturnType() instanceof PsiClassType) {
          PsiParameterList list = method.getParameterList();
          if (list.getParametersCount() == 3) {
            PsiParameter[] parameters = list.getParameters();
            if (EntryStream.zip(parameters, HANDLER_ARGUMENT_TYPES).allMatch((p, t) -> TypeUtils.typeEquals(t, p.getType())) &&
                SyntaxTraverser.psiTraverser(holder.getFile()).filter(PsiMethodReferenceExpression.class)
                  .filter(ref -> ref.isReferenceTo(method) && TypeUtils.typeEquals(HANDLER_CLASS, ref.getFunctionalInterfaceType()))
                  .first() != null) {
              check(method);
            }
          }
        }
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression lambda) {
        if (lambda.getParameterList().getParametersCount() != 3) return;
        PsiType type = lambda.getFunctionalInterfaceType();
        if (!InheritanceUtil.isInheritor(type, HANDLER_CLASS)) return;
        check(lambda);
      }

      private void check(PsiParameterListOwner method) {
        PsiElement body = method.getBody();
        if (body == null) return;
        PsiParameter methodParameter = method.getParameterList().getParameter(1);
        if (methodParameter == null) return;
        if (body instanceof PsiCodeBlock && !ControlFlowUtils.containsReturn(body)) return;
        if (!VariableAccessUtils.variableIsUsed(methodParameter, body)) {
          holder.registerProblem(Objects.requireNonNull(methodParameter.getNameIdentifier()),
                                 JavaAnalysisBundle.message("suspicious.invocation.handler.implementation.method.unused.message"));
          return;
        }
        InvocationHandlerAnalysisRunner runner = new InvocationHandlerAnalysisRunner(holder, body, methodParameter);
        DfaVariableValue methodName = runner.myDfaMethodName;
        if (methodName == null) return;
        Map<String, Map<PsiExpression, DfType>> returnMap = new TreeMap<>();
        RunnerResult result = runner.analyzeMethod(body, new JavaDfaListener() {
          @Override
          public void beforeValueReturn(@NotNull DfaValue value,
                                        @Nullable PsiExpression expression,
                                        @NotNull PsiElement context,
                                        @NotNull DfaMemoryState state) {
            if (context != method) return;
            String name = state.getDfType(methodName).getConstantOfType(String.class);
            if (name == null) {
              runner.cancel();
              return;
            }
            DfType type = state.getDfType(value);
            if (type instanceof DfPrimitiveType) {
              type = DfTypes.typedObject(((DfPrimitiveType)type).getPsiType().getBoxedType(body), Nullability.NOT_NULL);
            }
            returnMap.computeIfAbsent(name, k -> new HashMap<>()).merge(expression, type, DfType::join);
          }
        });
        if (result != RunnerResult.OK) return;
        Set<PsiExpression> reportedAnchors = new HashSet<>();
        returnMap.forEach((name, map) -> {
          DfType reduced = map.values().stream().reduce(DfType.BOTTOM, DfType::join);
          PsiType wantedType = getWantedType(body, name);
          if (wantedType == null) return;
          TypeConstraint wantedConstraint = TypeConstraints.exact(wantedType);
          if (reduced.meet(wantedConstraint.asDfType().meet(DfTypes.NOT_NULL_OBJECT)) != DfType.BOTTOM &&
              StreamEx.ofValues(map).map(DfaNullability::fromDfType).anyMatch(
                n -> n != DfaNullability.NULLABLE && n != DfaNullability.NULL)) {
            return;
          }
          map.forEach((expression, type) -> {
            if (reportedAnchors.contains(expression)) return;
            String message = null;
            TypeConstraint constraint = TypeConstraint.fromDfType(type);
            if (wantedConstraint.meet(constraint) == TypeConstraints.BOTTOM) {
              message = JavaAnalysisBundle.message("suspicious.invocation.handler.implementation.type.mismatch.message", name,
                                                   wantedConstraint.getPresentationText(null), constraint.getPresentationText(null));
            }
            DfaNullability nullability = DfaNullability.fromDfType(type);
            if (nullability == DfaNullability.NULL) {
              message = name.equals("toString") ?
                        JavaAnalysisBundle.message("suspicious.invocation.handler.implementation.null.returned.for.toString.message") :
                        JavaAnalysisBundle.message("suspicious.invocation.handler.implementation.null.returned.message", name);
            }
            if (message != null) {
              reportedAnchors.add(expression);
              holder.registerProblem(expression, message);
            }
          });
        });
      }

      private static @Nullable PsiType getWantedType(PsiElement body, String name) {
        return switch (name) {
          case "equals" -> PsiTypes.booleanType().getBoxedType(body);
          case "hashCode" -> PsiTypes.intType().getBoxedType(body);
          case "toString" -> PsiType.getJavaLangString(body.getManager(), body.getResolveScope());
          default -> null;
        };
      }
    };
  }

  private static class InvocationHandlerAnalysisRunner extends StandardDataFlowRunner {
    private final PsiElement myBody;
    private DfaVariableValue myDfaMethodName;
    private DfaVariableValue myDfaMethodDeclaringClass;
    private PsiType myStringType;
    private PsiType myObjectType;
    private PsiType myClassType;

    InvocationHandlerAnalysisRunner(@NotNull ProblemsHolder holder,
                                    PsiElement body,
                                    PsiParameter methodParameter) {
      super(holder.getProject());
      myBody = body;
      PsiClass methodClass = PsiUtil.resolveClassInClassTypeOnly(methodParameter.getType());
      if (methodClass == null) return;
      PsiMethod[] getNameMethods = methodClass.findMethodsByName("getName", false);
      if (getNameMethods.length != 1) return;
      PsiMethod getNameMethod = getNameMethods[0];
      if (!getNameMethod.getParameterList().isEmpty()) return;
      PsiMethod[] getDeclaringClassMethods = methodClass.findMethodsByName("getDeclaringClass", false);
      if (getDeclaringClassMethods.length != 1) return;
      PsiMethod getDeclaringClassMethod = getDeclaringClassMethods[0];
      if (!getDeclaringClassMethod.getParameterList().isEmpty()) return;
      myClassType = getDeclaringClassMethod.getReturnType();
      myStringType = getNameMethod.getReturnType();
      myObjectType = PsiType.getJavaLangObject(methodClass.getManager(), methodClass.getResolveScope());
      if (myClassType == null || myStringType == null) return;
      DfaValueFactory factory = getFactory();
      DfaVariableValue dfaMethod = PlainDescriptor.createVariableValue(factory, methodParameter);
      myDfaMethodName = (DfaVariableValue)new GetterDescriptor(getNameMethod).createValue(factory, dfaMethod);
      myDfaMethodDeclaringClass =
        (DfaVariableValue)new GetterDescriptor(getDeclaringClassMethod).createValue(factory, dfaMethod);
    }

    @Override
    protected @NotNull List<DfaInstructionState> createInitialInstructionStates(@NotNull PsiElement psiBlock,
                                                                                @NotNull Collection<? extends DfaMemoryState> memStates,
                                                                                @NotNull ControlFlow flow) {
      if (psiBlock != myBody) {
        return super.createInitialInstructionStates(psiBlock, memStates, flow);
      }
      List<DfaInstructionState> result = new ArrayList<>();
      Instruction instruction = flow.getInstruction(0);
      DfaVariableValue qualifier = myDfaMethodName.getQualifier();
      flow.keepVariables(desc ->
        desc.equals(myDfaMethodDeclaringClass.getDescriptor()) ||
        desc.equals(myDfaMethodName.getDescriptor()) ||
        (qualifier != null && desc.equals(qualifier.getDescriptor())));
      for (DfaMemoryState state : memStates) {
        state.applyCondition(myDfaMethodDeclaringClass.eq(DfTypes.constant(myObjectType, myClassType)));
        for (String methodName : Arrays.asList("hashCode", "equals", "toString")) {
          DfaMemoryState methodSpecificState = state.createCopy();
          methodSpecificState.applyCondition(myDfaMethodName.eq(DfTypes.constant(methodName, myStringType)));
          result.add(new DfaInstructionState(instruction, methodSpecificState));
        }
      }
      return result;
    }
  }
}
