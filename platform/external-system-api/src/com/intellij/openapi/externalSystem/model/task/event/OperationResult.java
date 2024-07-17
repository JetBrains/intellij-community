// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event;

import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 */
public class OperationResult implements Serializable {

  private final long myStartTime;
  private final long myEndTime;

  public OperationResult(long startTime, long endTime) {
    myStartTime = startTime;
    myEndTime = endTime;
  }

  public long getStartTime() {
    return myStartTime;
  }

  public long getEndTime() {
    return myEndTime;
  }
}
