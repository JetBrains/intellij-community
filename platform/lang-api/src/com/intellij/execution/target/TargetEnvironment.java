// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents created target environment. It might be local machine,
 * local or remote Docker container, SSH machine or any other machine
 * that is able to run processes on it.
 */
@ApiStatus.Experimental
public abstract class TargetEnvironment {
  private final TargetEnvironmentRequest myRequest;

  public TargetEnvironment(TargetEnvironmentRequest request) {
    myRequest = request;
  }

  @NotNull
  public abstract Process createProcess(@NotNull TargetedCommandLine commandLine, @NotNull ProgressIndicator indicator)
    throws ExecutionException;

  @NotNull
  public abstract TargetPlatform getRemotePlatform();

  //FIXME: document
  public abstract void shutdown();

  //@NotNull
  //public final Iterable<? extends TargetEnvironmentRequest.Volume> getVolumes() {
  //  return myRequest.getVolumes();
  //}

  public final TargetEnvironmentRequest getRequest() {
    return myRequest;
  }
}
