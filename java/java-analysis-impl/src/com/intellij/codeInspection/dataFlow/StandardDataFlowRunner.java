// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaInterceptor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
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
  // Default complexity limit (maximum allowed attempts to process instruction).
  // Fail as too complex to process if certain instruction is executed more than this limit times.
  // Also used to calculate a threshold when states are forcibly merged.
  protected static final int DEFAULT_MAX_STATES_PER_BRANCH = 300;

  private final @NotNull DfaValueFactory myValueFactory;
  private final @NotNull ThreeState myIgnoreAssertions;
  private boolean myInlining = true;
  private JvmDataFlowInterpreter myInterpreter;

  public StandardDataFlowRunner(@NotNull Project project) {
    this(project, null);
  }

  public StandardDataFlowRunner(@NotNull Project project, @Nullable PsiElement context) {
    this(project, context, ThreeState.NO);
  }

  /**
   * @param project current project
   * @param context analysis context element (code block, class, expression, etc.); used to determine whether we can trust
 *                field initializers (e.g. we usually cannot if context is a constructor)
   * @param ignoreAssertions if true, assertion statements will be ignored, as if JVM is started with -da.
   */
  public StandardDataFlowRunner(@NotNull Project project,
                                @Nullable PsiElement context,
                                @NotNull ThreeState ignoreAssertions) {
    myValueFactory = new DfaValueFactory(project, context);
    myIgnoreAssertions = ignoreAssertions;
  }

  public @NotNull DfaValueFactory getFactory() {
    return myValueFactory;
  }

  public final void cancel() {
    myInterpreter.cancel();
  }

  private @Nullable Collection<DfaMemoryState> createInitialStates(@NotNull PsiElement psiBlock,
                                                                   @NotNull DfaInterceptor interceptor,
                                                                   boolean allowInlining) {
    PsiElement container = PsiTreeUtil.getParentOfType(psiBlock, PsiClass.class, PsiLambdaExpression.class);
    if (container != null && (!(container instanceof PsiClass) || PsiUtil.isLocalOrAnonymousClass((PsiClass)container))) {
      PsiElement block = DfaPsiUtil.getTopmostBlockInSameClass(container.getParent());
      if (block != null) {
        final RunnerResult result;
        try {
          myInlining = allowInlining;
          result = analyzeMethod(block, interceptor);
        }
        finally {
          myInlining = true;
        }
        if (result == RunnerResult.OK || result == RunnerResult.CANCELLED) {
          final Collection<DfaMemoryState> closureStates = myInterpreter.getClosures().get(DfaPsiUtil.getTopmostBlockInSameClass(psiBlock));
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
   * @param interceptor an interceptor to use
   * @return result status
   */
  public final @NotNull RunnerResult analyzeMethod(@NotNull PsiElement psiBlock, @NotNull DfaInterceptor interceptor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, interceptor, false);
    return initialStates == null ? RunnerResult.NOT_APPLICABLE : analyzeMethod(psiBlock, interceptor, initialStates);
  }

  /**
   * Analyze this particular method (lambda, class initializer) trying to inline it into outer scope if possible.
   * Usually inlining works, e.g. for lambdas inside stream API calls.
   *
   * @param psiBlock method/lambda/class initializer body
   * @param interceptor an interceptor to use
   * @return result status
   */
  public final @NotNull RunnerResult analyzeMethodWithInlining(@NotNull PsiElement psiBlock, @NotNull DfaInterceptor interceptor) {
    Collection<DfaMemoryState> initialStates = createInitialStates(psiBlock, interceptor, true);
    if (initialStates == null) {
      return RunnerResult.NOT_APPLICABLE;
    }
    if (initialStates.isEmpty()) {
      return RunnerResult.OK;
    }
    return analyzeMethod(psiBlock, interceptor, initialStates);
  }

  /**
   * Analyze given code-block without analyzing any parent and children context
   * @param block block to analyze
   * @param interceptor an interceptor to use
   * @return result status
   */
  public final RunnerResult analyzeCodeBlock(@NotNull PsiCodeBlock block, @NotNull DfaInterceptor interceptor) {
    return analyzeMethod(block, interceptor, Collections.singleton(createMemoryState()));
  }

  final @NotNull RunnerResult analyzeMethod(@NotNull PsiElement psiBlock,
                                            @NotNull DfaInterceptor interceptor,
                                            @NotNull Collection<? extends DfaMemoryState> initialStates) {
    ControlFlow flow = buildFlow(psiBlock);
    if (flow == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeFlow(psiBlock, interceptor, initialStates, flow);
  }

  @NotNull RunnerResult analyzeFlow(@NotNull PsiElement psiBlock,
                                    @NotNull DfaInterceptor interceptor,
                                    @NotNull Collection<? extends DfaMemoryState> initialStates,
                                    ControlFlow flow) {
    List<DfaInstructionState> startingStates = createInitialInstructionStates(psiBlock, initialStates, flow);
    if (startingStates.isEmpty()) {
      return RunnerResult.ABORTED;
    }

    return interpret(interceptor, flow, startingStates);
  }

  protected final @Nullable ControlFlow buildFlow(@NotNull PsiElement psiBlock) {
    try {
      return ControlFlowAnalyzer.buildFlow(psiBlock, myValueFactory, myInlining);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (RuntimeException | AssertionError e) {
      reportDfaProblem(psiBlock, null, null, e);
    }
    return null;
  }

  protected final @NotNull RunnerResult interpret(@NotNull DfaInterceptor interceptor,
                                                  @NotNull ControlFlow flow,
                                                  @NotNull List<DfaInstructionState> startingStates) {
    myInterpreter = createInterpreter(interceptor, flow);
    return myInterpreter.interpret(startingStates);
  }

  @NotNull
  protected JvmDataFlowInterpreter createInterpreter(@NotNull DfaInterceptor interceptor,
                                                     @NotNull ControlFlow flow) {
    return new JvmDataFlowInterpreter(flow, interceptor);
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

  static void reportDfaProblem(@NotNull PsiElement psiBlock,
                               ControlFlow flow,
                               DfaInstructionState lastInstructionState, Throwable e) {
    Attachment[] attachments = {new Attachment("method_body.txt", psiBlock.getText())};
    if (flow != null) {
      String flowText = flow.toString();
      if (lastInstructionState != null) {
        int index = lastInstructionState.getInstruction().getIndex();
        flowText = flowText.replaceAll("(?m)^", "  ");
        flowText = flowText.replaceFirst("(?m)^ {2}" + index + ": ", "* " + index + ": ");
      }
      attachments = ArrayUtil.append(attachments, new Attachment("flow.txt", flowText));
      if (lastInstructionState != null) {
        DfaMemoryState memoryState = lastInstructionState.getMemoryState();
        String memStateText = null;
        try {
          memStateText = memoryState.toString();
        }
        catch (RuntimeException second) {
          e.addSuppressed(second);
        }
        if (memStateText != null) {
          attachments = ArrayUtil.append(attachments, new Attachment("memory_state.txt", memStateText));
        }
      }
    }
    if (e instanceof RuntimeExceptionWithAttachments) {
      attachments = ArrayUtil.mergeArrays(attachments, ((RuntimeExceptionWithAttachments)e).getAttachments());
    }
    LOG.error(new RuntimeExceptionWithAttachments(e, attachments));
  }

  public @NotNull RunnerResult analyzeMethodRecursively(@NotNull PsiElement block, @NotNull DfaInterceptor interceptor) {
    Collection<DfaMemoryState> states = createInitialStates(block, interceptor, false);
    if (states == null) return RunnerResult.NOT_APPLICABLE;
    return analyzeBlockRecursively(block, states, interceptor);
  }

  public @NotNull RunnerResult analyzeBlockRecursively(@NotNull PsiElement block,
                                                       @NotNull Collection<? extends DfaMemoryState> states,
                                                       @NotNull DfaInterceptor interceptor) {
    RunnerResult result = analyzeMethod(block, interceptor, states);
    if (result != RunnerResult.OK) return result;

    Ref<RunnerResult> ref = Ref.create(RunnerResult.OK);
    forNestedClosures((closure, nestedStates) -> {
      RunnerResult res = analyzeBlockRecursively(closure, nestedStates, interceptor);
      if (res != RunnerResult.OK) {
        ref.set(res);
      }
    });
    return ref.get();
  }

  protected @NotNull DfaMemoryState createMemoryState() {
    return new DfaMemoryStateImpl(myValueFactory);
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
      Collection<? extends DfaMemoryState> states = closures.get(closure);
      if (!unusedVars.isEmpty()) {
        List<DfaMemoryStateImpl> stateList = StreamEx.of(states)
          .peek(state -> unusedVars.forEach(state::flushVariable))
          .map(state -> (DfaMemoryStateImpl)state).distinct().toList();
        states = StateQueue.mergeGroup(stateList);
      }
      consumer.accept(closure, states);
    }
  }
}
