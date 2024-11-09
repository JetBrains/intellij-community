// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.interpreter.StateQueue;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A facade to run dataflow analysis on Java
 */
public class StandardDataFlowRunner {
  private static final Logger LOG = Logger.getInstance(StandardDataFlowRunner.class);

  private final @NotNull DfaValueFactory myValueFactory;
  private final @NotNull ThreeState myIgnoreAssertions;
  private boolean myInlining = true;
  private StandardDataFlowInterpreter myInterpreter;

  /**
   * @param project current project
   */
  public StandardDataFlowRunner(@NotNull Project project) {
    this(project, ThreeState.NO);
  }

  /**
   * @param project current project
   * @param ignoreAssertions assertion status: YES (-da), NO (-ea), or UNSURE (either)
   */
  public StandardDataFlowRunner(@NotNull Project project, @NotNull ThreeState ignoreAssertions) {
    myValueFactory = new DfaValueFactory(project);
    myIgnoreAssertions = ignoreAssertions;
  }

  public @NotNull DfaValueFactory getFactory() {
    return myValueFactory;
  }

  public final void cancel() {
    myInterpreter.cancel();
  }

  private @Nullable Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock,
                                                                   @NotNull DfaListener listener,
                                                                   boolean allowInlining) {
    PsiElement container = PsiTreeUtil.getParentOfType(psiBlock, PsiClass.class, PsiLambdaExpression.class);
    if (container != null && (!(container instanceof PsiClass) || PsiUtil.isLocalOrAnonymousClass((PsiClass)container))) {
      PsiElement block = DfaPsiUtil.getTopmostBlockInSameClass(container.getParent());
      if (block != null) {
        final RunnerResult result;
        try {
          myInlining = allowInlining;
          result = analyzeMethod(block, listener);
        }
        finally {
          myInlining = true;
        }
        if (result == RunnerResult.OK || result == RunnerResult.CANCELLED) {
          PsiElement topmostBlock = DfaPsiUtil.getTopmostBlockInSameClass(psiBlock);
          final Collection<DfaMemoryState> closureStates = topmostBlock == null ? List.of() : myInterpreter.getClosures().get(topmostBlock);
          if (allowInlining || !closureStates.isEmpty()) {
            return closureStates;
          }
        }
        return null;
      }
    }

    return Collections.singletonList(createMemoryState());
  }

  /**
   * Analyze this particular method (lambda, class initializer) without inlining this method into parent one.
   * E.g. if supplied method is a lambda within Stream API call chain, it still will be analyzed as separate method.
   * On the other hand, inlining will normally work inside the supplied method.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param listener a listener to use
   * @return result status
   */
  public final @NotNull RunnerResult analyzeMethod(@NotNull PsiElement psiBlock, @NotNull DfaListener listener) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, listener, false);
    return initialStates == null ? RunnerResult.NOT_APPLICABLE : analyzeMethod(psiBlock, listener, initialStates);
  }

  /**
   * Analyze this particular method (lambda, class initializer) trying to inline it into outer scope if possible.
   * Usually inlining works, e.g. for lambdas inside stream API calls.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param listener a listener to use
   * @return result status
   */
  public final @NotNull RunnerResult analyzeMethodWithInlining(@NotNull PsiElement psiBlock, @NotNull DfaListener listener) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, listener, true);
    if (initialStates == null) {
      return RunnerResult.NOT_APPLICABLE;
    }
    if (initialStates.isEmpty()) {
      return RunnerResult.OK;
    }
    return analyzeMethod(psiBlock, listener, initialStates);
  }

  final @NotNull RunnerResult analyzeMethod(@NotNull PsiElement psiBlock,
                                            @NotNull DfaListener listener,
                                            @NotNull Collection<? extends DfaMemoryState> initialStates) {
    ControlFlow flow = buildFlow(psiBlock);
    if (flow == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeFlow(psiBlock, listener, initialStates, flow);
  }

  @NotNull RunnerResult analyzeFlow(@NotNull PsiElement psiBlock,
                                    @NotNull DfaListener listener,
                                    @NotNull Collection<? extends DfaMemoryState> initialStates,
                                    ControlFlow flow) {
    List<DfaInstructionState> startingStates = createInitialInstructionStates(psiBlock, initialStates, flow);
    if (startingStates.isEmpty()) {
      return RunnerResult.ABORTED;
    }

    return interpret(listener, flow, startingStates);
  }

  protected final @Nullable ControlFlow buildFlow(@NotNull PsiElement psiBlock) {
    try {
      return ControlFlowAnalyzer.buildFlow(psiBlock, myValueFactory, myInlining);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (RuntimeException | AssertionError e) {
      LOG.error("Error building control flow", e, new Attachment("method_body.txt", psiBlock.getText()));
    }
    return null;
  }

  protected final @NotNull RunnerResult interpret(@NotNull DfaListener listener,
                                                  @NotNull ControlFlow flow,
                                                  @NotNull List<DfaInstructionState> startingStates) {
    myInterpreter = createInterpreter(listener, flow);
    RunnerResult result = myInterpreter.interpret(startingStates);
    afterInterpretation(flow, myInterpreter, result);
    return result;
  }

  protected void afterInterpretation(@NotNull ControlFlow flow, @NotNull StandardDataFlowInterpreter interpreter, @NotNull RunnerResult result) {
    
  }

  @NotNull
  protected StandardDataFlowInterpreter createInterpreter(@NotNull DfaListener listener, @NotNull ControlFlow flow) {
    return new StandardDataFlowInterpreter(flow, listener);
  }

  protected @NotNull List<DfaInstructionState> createInitialInstructionStates(@NotNull PsiElement psiBlock,
                                                                              @NotNull Collection<? extends DfaMemoryState> memStates,
                                                                              @NotNull ControlFlow flow) {
    DfaVariableValue assertionStatus = AssertionDisabledDescriptor.getAssertionsDisabledVar(myValueFactory);
    if (assertionStatus != null && myIgnoreAssertions != ThreeState.UNSURE) {
      DfaCondition condition = assertionStatus.eq(DfTypes.booleanValue(myIgnoreAssertions.toBoolean()));
      for (DfaMemoryState state : memStates) {
        state.applyCondition(condition);
      }
    }
    return ContainerUtil.map(memStates, s -> new DfaInstructionState(flow.getInstruction(0), s));
  }

  public boolean wasForciblyMerged() {
    return myInterpreter.wasForciblyMerged();
  }

  public @NotNull RunnerResult analyzeMethodRecursively(@NotNull PsiElement block, @NotNull DfaListener listener) {
    Collection<DfaMemoryState> states = createInitialStates(block, listener, false);
    if (states == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeBlockRecursively(block, states, listener);
  }

  public @NotNull RunnerResult analyzeBlockRecursively(@NotNull PsiElement block,
                                                       @NotNull Collection<? extends DfaMemoryState> states,
                                                       @NotNull DfaListener listener) {
    RunnerResult result = analyzeMethod(block, listener, states);
    if (result != RunnerResult.OK) return result;

    Ref<RunnerResult> ref = Ref.create(RunnerResult.OK);
    forNestedClosures((closure, nestedStates) -> {
      RunnerResult res = analyzeBlockRecursively(closure, nestedStates, listener);
      if (res != RunnerResult.OK) {
        ref.set(res);
      }
    });
    return ref.get();
  }

  protected @NotNull DfaMemoryState createMemoryState() {
    return new JvmDfaMemoryStateImpl(myValueFactory);
  }

  public void forNestedClosures(BiConsumer<? super PsiElement, ? super Collection<? extends DfaMemoryState>> consumer) {
    MultiMap<PsiElement, DfaMemoryState> closures = myInterpreter.getClosures();
    for (PsiElement closure : closures.keySet()) {
      List<DfaVariableValue> unusedVars = StreamEx.of(getFactory().getValues())
        .select(DfaVariableValue.class)
        .filter(var -> var.getQualifier() == null)
        .filter(var -> var.getPsiVariable() instanceof PsiVariable &&
                       !VariableAccessUtils.variableIsUsed((PsiVariable)var.getPsiVariable(), closure))
        .toList();
      Collection<DfaMemoryState> states = closures.get(closure);
      if (!unusedVars.isEmpty()) {
        List<DfaMemoryState> stateList = StreamEx.of(states)
          .peek(state -> state.forgetVariables(unusedVars::contains))
          .distinct().toList();
        states = StateQueue.squash(stateList);
      }
      consumer.accept(closure, states);
    }
  }
}
