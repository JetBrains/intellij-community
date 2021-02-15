// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

class MemoryAgentImpl implements MemoryAgent {
  private enum MemoryAgentActionState {
    RUNNING, FINISHED, CANCELLED
  }

  private static final Logger LOG = Logger.getInstance(MemoryAgentImpl.class);

  static final MemoryAgent DISABLED = new MemoryAgentImpl(MemoryAgentCapabilities.DISABLED);
  private final String cancellationFileName;
  private final MemoryAgentCapabilities myCapabilities;
  private MemoryAgentActionState myState;
  private File myCancellationFile;

  MemoryAgentImpl(@NotNull MemoryAgentCapabilities capabilities) {
    cancellationFileName = FileUtil.getTempDirectory() + "/" + "memoryAgentCancellationFile" + new Random().nextInt();
    myCapabilities = capabilities;
    myState = MemoryAgentActionState.FINISHED;
  }

  private <T> MemoryAgentActionResult<T> executeOperation(Callable<MemoryAgentActionResult<T>> callable) throws EvaluateException {
    if (myState == MemoryAgentActionState.RUNNING) {
      throw new EvaluateException("Some action is already running");
    }

    myState = MemoryAgentActionState.RUNNING;

    if (myCancellationFile != null) {
      FileUtil.delete(myCancellationFile);
      myCancellationFile = null;
    }

    try {
      return callable.call();
    }
    catch (Exception ex) {
      throw new EvaluateException(ex.getMessage());
    }
    finally {
      myState = MemoryAgentActionState.FINISHED;
    }
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<Pair<long[], ObjectReference[]>> estimateObjectSize(@NotNull EvaluationContextImpl evaluationContext,
                                                                                     @NotNull ObjectReference reference,
                                                                                     long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canEstimateObjectSize()) {
      throw new UnsupportedOperationException("Memory agent can't estimate object size");
    }


    return executeOperation(() -> MemoryAgentOperations.estimateObjectSize(evaluationContext, reference, cancellationFileName, timeoutInMillis));
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<long[]> estimateObjectsSizes(@NotNull EvaluationContextImpl evaluationContext,
                                                              @NotNull List<ObjectReference> references,
                                                              long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canEstimateObjectsSizes()) {
      throw new UnsupportedOperationException("Memory agent can't estimate objects sizes");
    }

    return executeOperation(() -> MemoryAgentOperations.estimateObjectsSizes(evaluationContext, references, cancellationFileName, timeoutInMillis));
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<long[]> getShallowSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                 @NotNull List<ReferenceType> classes,
                                                                 long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetShallowSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get shallow size by classes");
    }

    return executeOperation(() -> MemoryAgentOperations.getShallowSizeByClasses(evaluationContext, classes, cancellationFileName, timeoutInMillis));
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<long[]> getRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                  @NotNull List<ReferenceType> classes,
                                                                  long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetRetainedSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get retained size by classes");
    }

    return executeOperation(() -> MemoryAgentOperations.getRetainedSizeByClasses(evaluationContext, classes, cancellationFileName, timeoutInMillis));
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<Pair<long[], long[]>> getShallowAndRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                                          @NotNull List<ReferenceType> classes,
                                                                                          long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetRetainedSizeByClasses() || !myCapabilities.canGetShallowSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get shallow and retained size by classes");
    }

    return executeOperation(() -> MemoryAgentOperations.getShallowAndRetainedSizeByClasses(evaluationContext, classes, cancellationFileName, timeoutInMillis));
  }

  @NotNull
  @Override
  public MemoryAgentActionResult<ReferringObjectsInfo> findPathsToClosestGCRoots(@NotNull EvaluationContextImpl evaluationContext,
                                                                                 @NotNull ObjectReference reference,
                                                                                 int pathsNumber,
                                                                                 int objectsNumber,
                                                                                 long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canFindPathsToClosestGcRoots()) {
      throw new UnsupportedOperationException("Memory agent can't provide paths to closest gc roots");
    }

    return executeOperation(() -> MemoryAgentOperations.findPathsToClosestGCRoots(evaluationContext, reference, pathsNumber, objectsNumber, cancellationFileName, timeoutInMillis));
  }

  @Override
  public void cancelAction() {
    if (myState == MemoryAgentActionState.RUNNING) {
      try {
        myCancellationFile = FileUtil.createTempFile(cancellationFileName, "", true);
        myState = MemoryAgentActionState.CANCELLED;
      }
      catch (IOException ex) {
        LOG.error("Couldn't create memory agent cancellation file", ex);
      }
    }
  }

  @NotNull
  @Override
  public MemoryAgentCapabilities capabilities() {
    return myCapabilities;
  }
}
