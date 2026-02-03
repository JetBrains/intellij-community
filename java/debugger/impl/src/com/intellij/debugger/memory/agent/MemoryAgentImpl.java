// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

class MemoryAgentImpl implements MemoryAgent {
  private enum MemoryAgentActionState {
    RUNNING, FINISHED, CANCELLED
  }

  private static final Logger LOG = Logger.getInstance(MemoryAgentImpl.class);

  static final MemoryAgent DISABLED = new MemoryAgentImpl();
  private final IdeaNativeAgentProxyMirror myProxy;
  private MemoryAgentProgressTracker myProgressTracker;
  private MemoryAgentCapabilities myCapabilities;
  private MemoryAgentActionState myState;
  private File myCancellationFile;
  private ProgressIndicator myProgressIndicator;

  public static MemoryAgent createMemoryAgent(@NotNull EvaluationContextImpl evaluationContext) throws EvaluateException {
    MemoryAgentImpl memoryAgent = new MemoryAgentImpl();
    memoryAgent.myCapabilities = memoryAgent.initializeCapabilities(evaluationContext);
    if (memoryAgent.myCapabilities.isDisabled()) {
      return DISABLED;
    }
    return memoryAgent;
  }

  private MemoryAgentImpl() {
    String tempDir = FileUtil.getTempDirectory() + "/";
    int version = new Random().nextInt();
    myProxy = new IdeaNativeAgentProxyMirror(
      tempDir + "memoryAgentCancellationFile" + version,
      tempDir + "memoryAgentProgressFile" + version + ".json"
    );
    myCapabilities = MemoryAgentCapabilities.DISABLED;
    myState = MemoryAgentActionState.FINISHED;
    myProgressTracker = MemoryAgentProgressTracker.DISABLED;
  }

  private @NotNull MemoryAgentCapabilities initializeCapabilities(@NotNull EvaluationContextImpl evaluationContext) throws EvaluateException {
    return myProxy.initializeCapabilities(evaluationContext);
  }

  private <T> MemoryAgentActionResult<T> executeOperation(Callable<MemoryAgentActionResult<T>> callable) throws EvaluateException {
    if (myState == MemoryAgentActionState.RUNNING) {
      throw new EvaluateException("Some action is already running");
    }

    if (myCancellationFile != null) {
      FileUtil.delete(myCancellationFile);
      myCancellationFile = null;
    }

    if (myProgressIndicator != null) {
      myProgressTracker = new MemoryAgentProgressTrackerImpl(this, myProgressIndicator);
    }
    else {
      myProgressTracker = MemoryAgentProgressTracker.DISABLED;
    }

    try {
      myState = MemoryAgentActionState.RUNNING;
      myProgressTracker.startMonitoringProgress();
      return callable.call();
    }
    catch (Exception ex) {
      throw new EvaluateException(ex.getMessage());
    }
    finally {
      myProgressTracker.stopMonitoringProgress();
      myProgressIndicator = null;
      myProgressTracker = MemoryAgentProgressTracker.DISABLED;
      FileUtil.delete(new File(myProxy.getProgressFileName()));
      myState = MemoryAgentActionState.FINISHED;
    }
  }

  @Override
  public @NotNull MemoryAgentActionResult<Pair<long[], ObjectReference[]>> estimateObjectSize(@NotNull EvaluationContextImpl evaluationContext,
                                                                                              @NotNull ObjectReference reference,
                                                                                              long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canEstimateObjectSize()) {
      throw new UnsupportedOperationException("Memory agent can't estimate object size");
    }


    return executeOperation(() -> myProxy.estimateObjectSize(evaluationContext, reference, timeoutInMillis));
  }

  @Override
  public @NotNull MemoryAgentActionResult<Pair<long[], long[]>> getShallowAndRetainedSizesByObjects(@NotNull EvaluationContextImpl evaluationContext,
                                                                                                    @NotNull List<ObjectReference> references,
                                                                                                    long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canEstimateObjectsSizes()) {
      throw new UnsupportedOperationException("Memory agent can't estimate objects sizes");
    }

    return executeOperation(() -> myProxy.getShallowAndRetainedSizesByObjects(evaluationContext, references, timeoutInMillis));
  }

  @Override
  public @NotNull MemoryAgentActionResult<ObjectsAndSizes> getSortedShallowAndRetainedSizesByClass(@NotNull EvaluationContextImpl evaluationContext,
                                                                                                   @NotNull ReferenceType classType,
                                                                                                   long objectsLimit,
                                                                                                   long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canEstimateObjectsSizes()) {
      throw new UnsupportedOperationException("Memory agent can't estimate objects sizes");
    }

    return executeOperation(() -> myProxy.getShallowAndRetainedSizeByClass(evaluationContext, classType, objectsLimit, timeoutInMillis));
  }

  @Override
  public @NotNull MemoryAgentActionResult<long[]> getShallowSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                          @NotNull List<ReferenceType> classes,
                                                                          long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetShallowSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get shallow size by classes");
    }

    return executeOperation(() -> myProxy.getShallowSizeByClasses(evaluationContext, classes, timeoutInMillis));
  }

  @Override
  public @NotNull MemoryAgentActionResult<long[]> getRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                           @NotNull List<ReferenceType> classes,
                                                                           long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetRetainedSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get retained size by classes");
    }

    return executeOperation(() -> myProxy.getRetainedSizeByClasses(evaluationContext, classes, timeoutInMillis));
  }

  @Override
  public @NotNull MemoryAgentActionResult<Pair<long[], long[]>> getShallowAndRetainedSizeByClasses(@NotNull EvaluationContextImpl evaluationContext,
                                                                                                   @NotNull List<ReferenceType> classes,
                                                                                                   long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canGetRetainedSizeByClasses() || !myCapabilities.canGetShallowSizeByClasses()) {
      throw new UnsupportedOperationException("Memory agent can't get shallow and retained size by classes");
    }

    return executeOperation(() -> myProxy.getShallowAndRetainedSizeByClasses(evaluationContext, classes, timeoutInMillis));
  }

  @Override
  public @NotNull MemoryAgentActionResult<ReferringObjectsInfo> findPathsToClosestGCRoots(@NotNull EvaluationContextImpl evaluationContext,
                                                                                          @NotNull ObjectReference reference,
                                                                                          int pathsNumber,
                                                                                          int objectsNumber,
                                                                                          long timeoutInMillis) throws EvaluateException {
    if (!myCapabilities.canFindPathsToClosestGcRoots()) {
      throw new UnsupportedOperationException("Memory agent can't provide paths to closest gc roots");
    }

    return executeOperation(() -> myProxy.findPathsToClosestGCRoots(evaluationContext, reference, pathsNumber, objectsNumber, timeoutInMillis));
  }

  @Override
  public void cancelAction() {
    if (myState == MemoryAgentActionState.RUNNING) {
      try {
        myProgressTracker.cancelMonitoringProgress();
        myCancellationFile = FileUtil.createTempFile(myProxy.getCancellationFileName(), "", true);
        myState = MemoryAgentActionState.CANCELLED;
      }
      catch (IOException ex) {
        LOG.error("Couldn't create memory agent cancellation file", ex);
      }
    }
  }

  @Override
  public void setProgressIndicator(@NotNull ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
  }

  @Override
  public @Nullable MemoryAgentProgressPoint checkProgress() {
    return myProxy.checkProgress();
  }

  @Override
  public @NotNull MemoryAgentCapabilities getCapabilities() {
    return myCapabilities;
  }

  @Override
  public boolean isDisabled() {
    return this == DISABLED;
  }

  private interface MemoryAgentProgressTracker {
    MemoryAgentProgressTracker DISABLED = new MemoryAgentProgressTracker() {
    };

    default void startMonitoringProgress() {

    }

    default void cancelMonitoringProgress() {

    }

    default void stopMonitoringProgress() {

    }
  }

  private static class MemoryAgentProgressTrackerImpl implements MemoryAgentProgressTracker {
    private static final int PROGRESS_CHECKING_DELAY_MS = 500;
    private final ProgressIndicator myProgressIndicator;
    private final MemoryAgent myAgent;
    private final ReentrantLock myProgressIndicatorLock = new ReentrantLock();
    private ScheduledFuture<?> myProgressCheckingFuture;

    MemoryAgentProgressTrackerImpl(@NotNull MemoryAgent agent, @NotNull ProgressIndicator progressIndicator) {
      myAgent = agent;
      myProgressIndicator = progressIndicator;
    }

    @Override
    public void startMonitoringProgress() {
      myProgressIndicator.start();
      myProgressCheckingFuture = AppExecutorUtil.getAppScheduledExecutorService()
        .scheduleWithFixedDelay(this::updateProgress, 0, PROGRESS_CHECKING_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void cancelMonitoringProgress() {
      stopMonitoringProgress(true);
    }

    @Override
    public void stopMonitoringProgress() {
      stopMonitoringProgress(false);
    }

    private void stopMonitoringProgress(boolean cancel) {
      try {
        myProgressIndicatorLock.lock();
        if (myProgressCheckingFuture != null) {
          myProgressCheckingFuture.cancel(true);
        }

        if (myProgressIndicator.isRunning()) {
          if (cancel) {
            myProgressIndicator.cancel();
          }
          else {
            myProgressIndicator.stop();
          }
        }

        myProgressCheckingFuture = null;
      }
      finally {
        myProgressIndicatorLock.unlock();
      }
    }

    @SuppressWarnings("HardCodedStringLiteral")
    private void updateProgress() {
      ApplicationManager.getApplication().assertIsNonDispatchThread();

      MemoryAgentProgressPoint progressPoint = myAgent.checkProgress();
      if (progressPoint == null) {
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> {
        try {
          myProgressIndicatorLock.lock();
          if (myProgressIndicator.isRunning()) {
            myProgressIndicator.setText(progressPoint.getMessage());
            myProgressIndicator.setFraction(progressPoint.getFraction());
          }
        }
        finally {
          myProgressIndicatorLock.unlock();
        }
      });

      if (progressPoint.isFinished()) {
        stopMonitoringProgress();
      }
    }
  }
}
