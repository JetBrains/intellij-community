// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.impl.MethodNotFoundException;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.rt.debugger.BatchEvaluatorServer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BatchEvaluator {
  private static final Logger LOG = Logger.getInstance(BatchEvaluator.class);

  private static final Key<BatchEvaluator> BATCH_EVALUATOR_KEY = new Key<>("BatchEvaluator");
  public static final Key<Boolean> REMOTE_SESSION_KEY = new Key<>("is_remote_session_key");

  private final HashMap<SuspendContext, List<ToStringCommand>> myBuffer = new HashMap<>();

  private BatchEvaluator() {
  }

  public void invoke(ToStringCommand command) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    EvaluationContext evaluationContext = command.getEvaluationContext();
    SuspendContextImpl suspendContext = (SuspendContextImpl)evaluationContext.getSuspendContext();
    if (!(command.getValue() instanceof ObjectReference) || // skip for primitive values
        (!Registry.is("debugger.batch.evaluation.force") &&
        !Registry.is("debugger.batch.evaluation"))) {
      suspendContext.getManagerThread().invokeCommand(command);
    }
    else {
      List<ToStringCommand> toStringCommands = myBuffer.get(suspendContext);
      if (toStringCommands == null) {
        toStringCommands = new ArrayList<>();
        myBuffer.put(suspendContext, toStringCommands);
        suspendContext.getManagerThread().schedule(new BatchEvaluatorCommand(evaluationContext));
      }

      toStringCommands.add(command);
    }
  }

  public static BatchEvaluator getBatchEvaluator(EvaluationContext evaluationContext) {
    VirtualMachineProxyImpl virtualMachineProxy = ((EvaluationContextImpl)evaluationContext).getVirtualMachineProxy();
    BatchEvaluator batchEvaluator = virtualMachineProxy.getUserData(BATCH_EVALUATOR_KEY);

    if (batchEvaluator == null) {
      batchEvaluator = new BatchEvaluator();
      virtualMachineProxy.putUserData(BATCH_EVALUATOR_KEY, batchEvaluator);
    }
    return batchEvaluator;
  }

  private static boolean doEvaluateBatch(List<ToStringCommand> requests, EvaluationContext evaluationContext) {
    try {
      DebugProcess debugProcess = evaluationContext.getDebugProcess();
      List<Value> values = ContainerUtil.map(requests, ToStringCommand::getValue);

      if (ContainerUtil.exists(values, v -> !(v instanceof ObjectReference))) {
        LOG.error("Batch toString evaluation can only be used for object references");
        return false;
      }

      String helperMethodName;

      ArrayReference argArray = null;
      List<Value> args;
      if (values.size() > 10) {
        ArrayType objectArrayClass = (ArrayType)debugProcess.findClass(
          evaluationContext,
          "java.lang.Object[]",
          evaluationContext.getClassLoader());
        argArray = DebuggerUtilsEx.mirrorOfArray(objectArrayClass, values, evaluationContext);
        args = Collections.singletonList(argArray);
        helperMethodName = "evaluate";
      }
      else {
        args = values;
        helperMethodName = "evaluate" + values.size();
      }

      String value = DebuggerUtils.getInstance().processCollectibleValue(
        () -> DebuggerUtilsImpl.invokeHelperMethod((EvaluationContextImpl)evaluationContext, BatchEvaluatorServer.class, helperMethodName, args, false),
        result -> result instanceof StringReference ? ((StringReference)result).value() : null,
        evaluationContext);
      if (argArray != null) {
        DebuggerUtilsEx.enableCollection(argArray);
      }
      if (value != null) {
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
          int count = 0;
          while (dis.available() > 0) {
            boolean error = dis.readBoolean();
            String message = dis.readUTF();
            if (count >= requests.size()) {
              LOG.error("Invalid number of results: required " + requests.size() + ", reply = " + Arrays.toString(bytes));
              return false;
            }
            ToStringCommand command = requests.get(count++);
            if (error) {
              command.evaluationError(JavaDebuggerBundle.message("evaluation.error.method.exception", message));
            }
            else {
              command.evaluationResult(message);
            }
          }
        }
        catch (IOException e) {
          LOG.error("Failed to read batch response", e, "reply was " + Arrays.toString(bytes));
          return false;
        }
        return true;
      }
    }
    catch (ObjectCollectedException e) {
      LOG.error(e);
    }
    catch (MethodNotFoundException e) {
      if (IntelliJProjectUtil.isIntelliJPlatformProject(evaluationContext.getProject())) {
        String runProfileName = null;
        DebugProcessImpl debugProcess = (DebugProcessImpl)evaluationContext.getDebugProcess();
        XDebugSession session = debugProcess.getSession().getXDebugSession();
        if (session != null) {
          RunProfile runProfile = session.getRunProfile();
          if (runProfile != null) {
            runProfileName = runProfile.getName();
          }
        }
        if (runProfileName != null) {
          LOG.error("Unable to find helper method", e, "Run configuration: " + runProfileName);
        }
      }
      else {
        LOG.error(e);
      }
    }
    catch (EvaluateException e) {
      ObjectReference exceptionFromTargetVM = e.getExceptionFromTargetVM();
      if (exceptionFromTargetVM != null && "java.io.UTFDataFormatException".equals(exceptionFromTargetVM.referenceType().name())) {
        // one of the strings is too long - just fall back to the regular separate toString calls
      }
      else {
        LOG.error(e);
      }
    }
    return false;
  }

  private class BatchEvaluatorCommand extends PossiblySyncCommand {
    private final EvaluationContext myEvaluationContext;

    BatchEvaluatorCommand(EvaluationContext evaluationContext) {
      super((SuspendContextImpl)evaluationContext.getSuspendContext());
      myEvaluationContext = evaluationContext;
    }

    @Override
    public void syncAction(@NotNull SuspendContextImpl suspendContext) {
      List<ToStringCommand> commands = myBuffer.remove(suspendContext);

      if ((commands.size() == 1 && !Registry.is("debugger.batch.evaluation.force")) || !doEvaluateBatch(commands, myEvaluationContext)) {
        commands.forEach(ToStringCommand::action);
      }
    }

    @Override
    public void commandCancelled() {
      myBuffer.remove(getSuspendContext());
    }
  }
}
