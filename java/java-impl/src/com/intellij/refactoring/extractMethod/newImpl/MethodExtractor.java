// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.newImpl.structures.*;
import com.intellij.refactoring.util.VariableData;
import com.intellij.util.containers.ContainerUtil;

import static com.intellij.refactoring.extractMethod.newImpl.MethodExtractor.ReturnMode.*;
import static java.util.Objects.requireNonNull;

import java.util.*;

public class MethodExtractor {

  private final CodeFragment fragment;
  private final SignatureBuilder signatureBuilder;
  private final BodyBuilder bodyBuilder;
  private final CallBuilder callBuilder;

  private MethodExtractor(CodeFragment fragment,
                          SignatureBuilder signatureBuilder,
                          BodyBuilder bodyBuilder,
                          CallBuilder callBuilder) {
    this.fragment = fragment;
    this.signatureBuilder = signatureBuilder;
    this.bodyBuilder = bodyBuilder;
    this.callBuilder = callBuilder;
  }

  public MethodExtractor methodName(String name) {
    callBuilder.methodName(name);
    signatureBuilder.methodName(name);
    return this;
  }

  public MethodExtractor parameterNames(List<String> names) {
    bodyBuilder.inputSubstitutions(names);
    signatureBuilder.parameterNames(names);
    return this;
  }

  public MethodExtractor parameterTypes(List<PsiType> types) {
    signatureBuilder.parameterTypes(types);
    return this;
  }

  public MethodExtractor tryToRemapParameters(VariableData[] variables) {
    final List<VariableData> variablesData = Arrays.asList(variables);

    final List<String> newCallParameters = ContainerUtil.map(variablesData, variable -> variable.variable.getName());
    final List<String> oldCallParameters = callBuilder.parameters();

    if (!new HashSet<>(newCallParameters).equals(new HashSet<>(oldCallParameters))) {
      return this;
    }

    final List<Integer> indexes = ContainerUtil.map(newCallParameters, name -> oldCallParameters.indexOf(name));

    final List<String> newParameters = ContainerUtil.map(variablesData, variable -> variable.getName());
    final List<PsiType> newTypes = reorder(signatureBuilder.parameterTypes(), indexes);
    final List<List<PsiExpression>> newInputGroups = reorder(bodyBuilder.inputGroups(), indexes);

    bodyBuilder.inputSubstitutions(newParameters).inputGroups(newInputGroups);
    signatureBuilder.parameterNames(newParameters).parameterTypes(newTypes);
    callBuilder.parameters(newCallParameters);

    return this;
  }

  private static <T> List<T> reorder(List<T> list, List<Integer> indexes){
    return ContainerUtil.map(indexes, index -> list.get(index));
  }

  public static MethodExtractor getInstance(List<PsiElement> elements) {
    CodeFragment fragment = CodeFragment.of(elements);
    FlowDependency dependencies = FlowDependency.computeDependency(fragment);
    ReturnMode returnMode = findReturnMode(dependencies);
    assert returnMode != MultipleVariables;

    final BodyBuilder bodyBuilder = createBodyBuilder(returnMode, dependencies);
    final CallBuilder callBuilder = createCallBuilder(returnMode, dependencies);
    final SignatureBuilder signatureBuilder = createSignatureBuilder(returnMode, dependencies);

    List<String> defaultParameterNames = createDefaultParameterNames(dependencies);
    bodyBuilder.inputSubstitutions(defaultParameterNames);
    callBuilder.parameters(defaultParameterNames);
    signatureBuilder.parameterNames(defaultParameterNames);

    String defaultMethodName = "extracted";
    callBuilder.methodName(defaultMethodName);
    signatureBuilder.methodName(defaultMethodName);

    return new MethodExtractor(fragment, signatureBuilder, bodyBuilder, callBuilder);
  }

  public void extract() {
    final List<PsiStatement> callStatements = callBuilder.build();
    final PsiMethod extractedMethod = signatureBuilder.build();
    requireNonNull(extractedMethod.getBody()).replace(bodyBuilder.build());

    ApplicationManager.getApplication().runWriteAction(() -> {
      final PsiElement parentMethod = PsiTreeUtil.findFirstParent(fragment.getCommonParent(), element -> element instanceof PsiMethod);
      requireNonNull(parentMethod).getParent().addAfter(extractedMethod, parentMethod);

      fragment.getCommonParent()
        .addRangeBefore(callStatements.get(0), callStatements.get(callStatements.size() - 1), fragment.getFirstElement());
      fragment.getCommonParent().deleteChildRange(fragment.getFirstElement(), fragment.getLastElement());
    });
  }

  private static CallBuilder createCallBuilder(ReturnMode returnMode,
                                               FlowDependency dependencies) {
    final CallBuilder builder = new CallBuilder(dependencies.fragment.getProject());

    switch (returnMode) {
      case MethodCall:
        return builder
          .methodCall();
      case ConditionalExit:
        return builder
          .guardMethodCall(dependencies.exitGroups.get(0).statements.get(0).getText());
      case NullableExitVariable:
        return builder
          .declareVariable(findReturnType(returnMode, dependencies), "out")
          .returnNotNullVariable();
      case ReturnMethodCall:
        return builder
          .returnMethodCall();
      case NullableVariable:
        builder.guardNullVariable(dependencies.exitGroups.get(0).statements.get(0).getText());
      case Variable:
        final PsiVariable outVariable = guessOutputVariable(dependencies);
        if (isDeclaredInside(dependencies.fragment, outVariable)) {
          return builder.declareVariable(findReturnType(returnMode, dependencies), outVariable.getName());
        }
        else {
          return builder.assignToVariable(outVariable.getName());
        }
      default:
        throw new UnsupportedOperationException("Unsupported return mode: " + returnMode);
    }
  }

  private static boolean isDeclaredInside(CodeFragment fragment, PsiVariable variable) {
    return fragment.getTextRange().contains(variable.getTextRange());
  }

  private static PsiVariable guessOutputVariable(FlowDependency dependencies) {
    return dependencies.outputVariables.get(0);
  }

  private static SignatureBuilder createSignatureBuilder(ReturnMode returnMode, FlowDependency dependencies) {
    return new SignatureBuilder(dependencies.fragment.getProject())
      .makeStatic(isInsideStaticMethod(dependencies.fragment.getCommonParent()))
      .throwExceptions(dependencies.thrownExceptions)
      .parameterTypes(getDefaultParameterTypes(dependencies))
      .returnType(findReturnType(returnMode, dependencies));
  }

  private static List<PsiType> getDefaultParameterTypes(FlowDependency dependencies) {
    return dependencies.parameterTypes;
  }

  private static boolean isInsideStaticMethod(PsiElement element) {
    final PsiMethod parentMethod = PsiTreeUtil.getTopmostParentOfType(element, PsiMethod.class);
    return parentMethod.getModifierList().hasModifierProperty(PsiModifier.STATIC);
  }

  private static BodyBuilder createBodyBuilder(ReturnMode returnMode, FlowDependency dependencies) {
    final BodyBuilder builder = new BodyBuilder(dependencies.fragment)
      .inputGroups(ContainerUtil.map(dependencies.parameterGroups, it -> it.references))
      .missedDeclarations(dependencies.missedDeclarations);

    switch (returnMode) {
      case Variable:
        return builder.defaultReturn(dependencies.outputVariables.get(0).getName());
      case ConditionalExit:
        return builder
          .specialExits(dependencies.exitGroups.get(0).statements, "return true;")
          .defaultReturn("false");
      case ReturnMethodCall:
      case MethodCall:
        return builder;
      case NullableVariable:
        return builder
          .defaultReturn(dependencies.outputVariables.get(0).getName())
          .specialExits(dependencies.exitGroups.get(0).statements, "return null;");
      case NullableExitVariable:
        return builder.defaultReturn("null");
      default:
        throw new IllegalArgumentException("Unsupported return mode: " + returnMode);
    }
  }

  private static PsiType findReturnType(ReturnMode returnMode, FlowDependency dependency) {
    switch (returnMode) {
      case MethodCall:
        return PsiType.VOID;
      case NullableVariable:
        return getBoxedTypeOf(dependency.outputVariables.get(0));
      case ConditionalExit:
        return PsiType.BOOLEAN;
      case Variable:
        return dependency.outputTypes.get(0);
      case NullableExitVariable:
        return getBoxedTypeOf(findNotNullReturn(dependency.exitGroups.get(0).statements));
      case ReturnMethodCall:
        return findNotNullReturn(dependency.exitGroups.get(0).statements).getType();
      case MultipleVariables:
      default:
        throw new IllegalArgumentException();
    }
  }

  private static PsiExpression findNotNullReturn(List<PsiStatement> returnStatements) {
    return returnStatements.stream()
      .map(returnStatement -> ((PsiReturnStatement)returnStatement).getReturnValue())
      .filter(returnValue -> returnValue != null && returnValue.getType() != PsiType.NULL)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException());
  }

  private static PsiType getBoxedTypeOf(PsiExpression expression) {
    return boxTypeIfPossible(expression.getType(), expression);
  }

  private static PsiType getBoxedTypeOf(PsiVariable variable) {
    return boxTypeIfPossible(variable.getType(), variable);
  }

  private static PsiType boxTypeIfPossible(PsiType type, PsiElement context) {
    if (type instanceof PsiPrimitiveType) {
      return ((PsiPrimitiveType)type).getBoxedType(context);
    }
    else {
      return type;
    }
  }

  private static ReturnMode findReturnMode(FlowDependency dependencies) {
    final int outVars = dependencies.outputVariables.size();
    final int exitGroups = dependencies.exitGroups.size();
    if (outVars > 1) {
      return MultipleVariables;
    }
    if (outVars == 1) {
      if (exitGroups > 1) {
        return MultipleVariables;
      }
      if (exitGroups == 0) {
        return Variable;
      }
      else if (exitGroups == 1) {
        final boolean isNotNullOutVariable =
          FlowDependency.hasOnlyNotNullAssignments(dependencies.outputVariables.get(0), dependencies.fragment);
        final boolean areExitsSame = dependencies.exitGroups.get(0).areEffectivelySame();
        return isNotNullOutVariable && areExitsSame ? NullableVariable : MultipleVariables;
      }
    }

    if (exitGroups > 1) {
      return MultipleVariables;
    }

    if (exitGroups == 0) {
      return MethodCall;
    }

    if (exitGroups == 1) {
      final StatementGroup exitGroup = dependencies.exitGroups.get(0);
      if (!dependencies.canCompleteNormally || dependencies.hasSingleExit) {
        final PsiStatement exitStatement = exitGroup.statements.get(0);
        if (exitStatement instanceof PsiReturnStatement && ((PsiReturnStatement)exitStatement).getReturnValue() != null) {
          return ReturnMethodCall;
        }
        else {
          return MethodCall;
        }
      }

      if (exitGroup.areEffectivelySame()) {
        return dependencies.returnsLocalVariable ? NullableExitVariable : ConditionalExit;
      }
      else {
        final List<PsiReturnStatement> returns = ContainerUtil.map(exitGroup.statements, statement -> (PsiReturnStatement)statement);
        return FlowDependency.hasOnlyNotNullReturns(dependencies.fragment, returns) ? NullableExitVariable : MultipleVariables;
      }
    }

    throw new IllegalStateException();
  }

  private static List<String> createDefaultParameterNames(FlowDependency dependencies) {
    return createParameterNames(dependencies.inputVariables);
  }

  private static List<String> createParameterNames(List<PsiVariable> inputVariables) {
    return ContainerUtil.map(inputVariables, variable -> variable.getName());
  }

  enum ReturnMode {
    MethodCall, ReturnMethodCall, Variable, NullableVariable, ConditionalExit, NullableExitVariable, MultipleVariables
  }
}