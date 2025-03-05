// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.impl.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class ExternalSystemProgressEventConverter {

  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.externalSystem.event-processing");

  public static @Nullable BuildEvent convertBuildEvent(@NotNull ExternalSystemTaskExecutionEvent event) {
    var hint = event.getProgressEvent().getDescriptor().getHint();
    var buildEvent = convertAbstractBuildEvent(event);
    if (buildEvent != null) {
      buildEvent.setHint(hint);
    }
    return buildEvent;
  }

  private static @Nullable AbstractBuildEvent convertAbstractBuildEvent(@NotNull ExternalSystemTaskExecutionEvent event) {
    var progressEvent = event.getProgressEvent();
    var eventId = progressEvent.getEventId();
    var descriptor = progressEvent.getDescriptor();
    var eventTime = descriptor.getEventTime();
    var displayName = descriptor.getDisplayName();
    var parentEventId = ObjectUtils.chooseNotNull(progressEvent.getParentEventId(), event.getId());

    if (progressEvent instanceof ExternalSystemStartEvent) {
      return new StartEventImpl(eventId, parentEventId, eventTime, displayName);
    }
    else if (progressEvent instanceof ExternalSystemFinishEvent<?> finishEvent) {
      var operationResult = finishEvent.getOperationResult();
      var eventResult = convertEventResult(operationResult);
      if (eventResult != null) {
        return new FinishEventImpl(eventId, parentEventId, eventTime, displayName, eventResult);
      }
    }
    else if (progressEvent instanceof ExternalSystemStatusEvent<?> statusEvent) {
      var total = statusEvent.getTotal();
      var progress = statusEvent.getProgress();
      var unit = statusEvent.getUnit();
      return new ProgressBuildEventImpl(eventId, parentEventId, eventTime, displayName, total, progress, unit);
    }
    else if (progressEvent instanceof ExternalSystemMessageEvent<?> messageEvent) {
      var message = ObjectUtils.chooseNotNull(messageEvent.getMessage(), displayName);
      var isStdOut = messageEvent.isStdOut();
      return new OutputBuildEventImpl(eventId, parentEventId, message, isStdOut);
    }
    LOG.warn("Undefined progress event " + event.getClass().getSimpleName() + " " + event);
    return null;
  }

  private static @Nullable EventResult convertEventResult(@NotNull OperationResult result) {
    if (result instanceof FailureResult) {
      var failures = convertFailureResult((FailureResult)result);
      return new FailureResultImpl(failures);
    }
    else if (result instanceof SkippedResult) {
      return new SkippedResultImpl();
    }
    else if (result instanceof SuccessResult successResult) {
      return new SuccessResultImpl(successResult.isUpToDate());
    }
    return null;
  }

  private static @NotNull List<com.intellij.build.events.Failure> convertFailureResult(@NotNull FailureResult failure) {
    return ContainerUtil.map(failure.getFailures(), it -> convertFailure(it));
  }

  private static @NotNull com.intellij.build.events.Failure convertFailure(@NotNull Failure failure) {
    var causes = ContainerUtil.map(failure.getCauses(), it -> convertFailure(it));
    return new FailureImpl(failure.getMessage(), failure.getDescription(), causes);
  }
}
