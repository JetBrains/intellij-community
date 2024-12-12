// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.jvm.descriptors.AssertionDisabledDescriptor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.xdebugger.impl.dfaassist.DfaResult;
import com.sun.jdi.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Callable;

public final class DebuggerDfaRunnerUtils {
  @RequiresReadLock
  static @Nullable DebuggerDfaRunner.Larva hatch(@NotNull StackFrameProxyEx proxy, @Nullable PsiElement element) throws EvaluateException {
    if (element == null || !element.isValid()) return null;
    Project project = element.getProject();
    if (DumbService.isDumb(project)) return null;

    DfaAssistProvider provider = DfaAssistProvider.EP_NAME.forLanguage(element.getLanguage());
    if (provider == null) return null;
    try {
      if (!provider.locationMatches(element, proxy.location())) return null;
    }
    catch (IllegalArgumentException iea) {
      throw new EvaluateException(iea.getMessage(), iea);
    }
    PsiElement anchor = provider.getAnchor(element);
    if (anchor == null) return null;
    PsiElement body = provider.getCodeBlock(anchor);
    if (body == null) return null;
    DfaValueFactory factory = new DfaValueFactory(project);
    ControlFlow flow = DataFlowIRProvider.forElement(body, factory);
    if (flow == null) return null;
    long modificationStamp = PsiModificationTracker.getInstance(project).getModificationCount();
    int offset = flow.getStartOffset(anchor).getInstructionOffset();
    if (offset < 0) return null;
    Map<Value, List<DfaVariableValue>> jdiToDfa = createPreliminaryJdiMap(provider, anchor, flow, proxy);
    if (jdiToDfa.isEmpty()) return null;
    return new DebuggerDfaRunner.Larva(project, anchor, body, flow, factory, modificationStamp, provider, jdiToDfa, proxy, offset);
  }

  @NotNull
  private static Map<Value, List<DfaVariableValue>> createPreliminaryJdiMap(@NotNull DfaAssistProvider provider,
                                                                            @NotNull PsiElement anchor,
                                                                            @NotNull ControlFlow flow,
                                                                            @NotNull StackFrameProxyEx proxy) throws EvaluateException {
    DfaValueFactory factory = flow.getFactory();
    Set<VariableDescriptor> descriptors = StreamEx.of(flow.getInstructions()).flatCollection(inst -> inst.getRequiredDescriptors(factory))
      .toSet();
    Map<Value, List<DfaVariableValue>> myMap = new HashMap<>();
    StreamEx<DfaVariableValue> stream =
      StreamEx.of(factory.getValues().toArray(DfaValue.EMPTY_ARRAY))
        .flatMap(dfaValue -> StreamEx.of(descriptors).map(desc -> desc.createValue(factory, dfaValue)).append(dfaValue))
        .select(DfaVariableValue.class)
        .distinct();
    for (DfaVariableValue dfaVar : stream) {
      Value jdiValue = resolveJdiValue(provider, anchor, proxy, dfaVar);
      if (jdiValue != null) {
        myMap.computeIfAbsent(jdiValue, v -> new ArrayList<>()).add(dfaVar);
      }
    }
    return myMap;
  }

  @Nullable
  private static Value resolveJdiValue(@NotNull DfaAssistProvider provider,
                                       @NotNull PsiElement anchor,
                                       @NotNull StackFrameProxyEx proxy,
                                       @NotNull DfaVariableValue var) throws EvaluateException {
    if (var.getDescriptor() instanceof AssertionDisabledDescriptor) {
      Location location = proxy.location();
      ThreeState status = DebuggerUtilsEx.getEffectiveAssertionStatus(location);
      // Assume that assertions are enabled if we cannot fetch the status
      return location.virtualMachine().mirrorOf(status == ThreeState.NO);
    }
    return provider.getJdiValueForDfaVariable(proxy, var, anchor);
  }

  @Nullable
  static DebuggerDfaRunner.Pupa makePupa(@NotNull StackFrameProxyEx proxy, @NotNull SmartPsiElementPointer<PsiElement> pointer) {
    Callable<DebuggerDfaRunner.Larva> action = () -> {
      try {
        return hatch(proxy, pointer.getElement());
      }
      catch (VMDisconnectedException | VMOutOfMemoryException | InternalException |
             EvaluateException | InconsistentDebugInfoException | InvalidStackFrameException ignore) {
        return null;
      }
    };
    Project project = pointer.getProject();
    DebuggerDfaRunner.Larva larva = ReadAction.nonBlocking(action).withDocumentsCommitted(project).executeSynchronously();
    if (larva == null) return null;
    DebuggerDfaRunner.Pupa pupa;
    try {
      pupa = larva.pupate();
    }
    catch (VMDisconnectedException | VMOutOfMemoryException | InternalException |
           EvaluateException | InconsistentDebugInfoException | InvalidStackFrameException ignore) {
      return null;
    }
    return pupa;
  }

  public static @Nullable DebuggerDfaRunner createDfaRunner(@NotNull StackFrameProxyEx proxy,
                                                            @NotNull SmartPsiElementPointer<PsiElement> pointer) {
    DebuggerDfaRunner.Pupa pupa = makePupa(proxy, pointer);
    if (pupa == null) return null;
    return ReadAction.nonBlocking(pupa::transform).withDocumentsCommitted(pointer.getProject()).executeSynchronously();
  }

  static void scheduleDfaUpdate(final DfaAssist assist, @NotNull DebuggerContextImpl newContext, PsiElement element) {
    SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(element);
    Objects.requireNonNull(newContext.getManagerThread()).schedule(new SuspendContextCommandImpl(newContext.getSuspendContext()) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        StackFrameProxyImpl proxy = suspendContext.getFrameProxy();
        if (proxy == null) {
          assist.cleanUp();
          return;
        }
        DebuggerDfaRunner.Pupa runnerPupa = makePupa(proxy, pointer);
        if (runnerPupa == null) {
          assist.cleanUp();
          return;
        }
        var computation = ReadAction.nonBlocking(() -> {
            DebuggerDfaRunner runner = runnerPupa.transform();
            return runner == null ? DfaResult.EMPTY : runner.computeHints();
          })
          .withDocumentsCommitted(suspendContext.getDebugProcess().getProject())
          .coalesceBy(assist)
          .finishOnUiThread(ModalityState.nonModal(), hints -> assist.displayInlaysInternal(hints))
          .submit(AppExecutorUtil.getAppExecutorService());
        assist.setComputation(computation);
      }
    });
  }
}
