// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.jdi.ThreadReferenceProxy;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.rt.debugger.BatchEvaluatorServer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jdi.MethodImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class BatchEvaluator {
  private static final Logger LOG = Logger.getInstance(BatchEvaluator.class);

  private final DebugProcess myDebugProcess;
  private boolean myBatchEvaluatorChecked;
  private ClassType myBatchEvaluatorClass;
  private Method myBatchEvaluatorMethod;

  private static final Key<BatchEvaluator> BATCH_EVALUATOR_KEY = new Key<>("BatchEvaluator");
  public static final Key<Boolean> REMOTE_SESSION_KEY = new Key<>("is_remote_session_key");

  private final HashMap<SuspendContext, List<ToStringCommand>> myBuffer = new HashMap<>();

  private BatchEvaluator(DebugProcess process) {
    myDebugProcess = process;
    myDebugProcess.addDebugProcessListener(new DebugProcessListener() {
      @Override
      public void processDetached(@NotNull DebugProcess process, boolean closedByUser) {
        myBatchEvaluatorChecked = false;
        myBatchEvaluatorClass = null;
        myBatchEvaluatorMethod = null;
      }
    });
  }

  public boolean hasBatchEvaluator(EvaluationContext evaluationContext) {
    if (!myBatchEvaluatorChecked) {
      myBatchEvaluatorChecked = true;
      if (DebuggerUtilsImpl.isRemote(myDebugProcess)) {
        // optimization: for remote sessions the BatchEvaluator is not there for sure
        return false;
      }

      ThreadReferenceProxy thread = evaluationContext.getSuspendContext().getThread();

      if (thread == null) {
        return false;
      }

      ThreadReference threadReference = thread.getThreadReference();
      if (threadReference == null) {
        return false;
      }

      try {
        myBatchEvaluatorClass = (ClassType)myDebugProcess.findClass(evaluationContext, BatchEvaluatorServer.class.getName(),
                                                                    evaluationContext.getClassLoader());
      }
      catch (EvaluateException ignored) {
      }

      if (myBatchEvaluatorClass != null) {
        myBatchEvaluatorMethod = DebuggerUtils.findMethod(myBatchEvaluatorClass, "evaluate", "([Ljava/lang/Object;)Ljava/lang/String;");
      }
    }
    return myBatchEvaluatorMethod != null;
  }

  public void invoke(ToStringCommand command) {
    DebuggerManagerThreadImpl.assertIsManagerThread();

    final EvaluationContext evaluationContext = command.getEvaluationContext();
    final SuspendContext suspendContext = evaluationContext.getSuspendContext();

    if (!Registry.is("debugger.batch.evaluation.force") &&
        (!Registry.is("debugger.batch.evaluation") || !hasBatchEvaluator(evaluationContext))) {
      myDebugProcess.getManagerThread().invokeCommand(command);
    }
    else {
      List<ToStringCommand> toStringCommands = myBuffer.get(suspendContext);
      if (toStringCommands == null) {
        toStringCommands = new ArrayList<>();
        myBuffer.put(suspendContext, toStringCommands);
        ((DebuggerManagerThreadImpl)myDebugProcess.getManagerThread()).schedule(new BatchEvaluatorCommand(evaluationContext));
      }

      toStringCommands.add(command);
    }
  }

  public static BatchEvaluator getBatchEvaluator(DebugProcess debugProcess) {
    BatchEvaluator batchEvaluator = debugProcess.getUserData(BATCH_EVALUATOR_KEY);

    if (batchEvaluator == null) {
      batchEvaluator = new BatchEvaluator(debugProcess);
      debugProcess.putUserData(BATCH_EVALUATOR_KEY, batchEvaluator);
    }
    return batchEvaluator;
  }

  private boolean doEvaluateBatch(List<ToStringCommand> requests, EvaluationContext evaluationContext) {
    try {
      if (!hasBatchEvaluator(evaluationContext)) {
        return false;
      }
      DebugProcess debugProcess = evaluationContext.getDebugProcess();
      List<Value> values = ContainerUtil.map(requests, ToStringCommand::getValue);

      ArrayType objectArrayClass = (ArrayType)debugProcess.findClass(
        evaluationContext,
        "java.lang.Object[]",
        evaluationContext.getClassLoader());
      if (objectArrayClass == null) {
        return false;
      }

      // reserve one extra element for the return value to avoid gc collection of the result
      ArrayReference argArray = DebuggerUtilsEx.mirrorOfArray(objectArrayClass, values.size() + 1, evaluationContext);
      DebuggerUtilsEx.setArrayValues(argArray, values, false);

      Value result = ((DebugProcessImpl)debugProcess).invokeMethod(
        evaluationContext, myBatchEvaluatorClass, myBatchEvaluatorMethod, Collections.singletonList(argArray),
        MethodImpl.SKIP_ASSIGNABLE_CHECK, true);

      if (result instanceof StringReference stringReference) {
        byte[] bytes = stringReference.value().getBytes(StandardCharsets.ISO_8859_1);
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
    catch (EvaluateException | ClassNotLoadedException | InvalidTypeException e) {
      LOG.error(e);
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
