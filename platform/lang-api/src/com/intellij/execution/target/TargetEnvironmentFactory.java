// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A factory for creating target environment ({@link TargetEnvironment}) for given
 * target configuration ({@link TargetEnvironmentConfiguration}.
 * <p>
 * A factory of a particular {@link TargetEnvironmentType} can be obtained
 * with {@link TargetEnvironmentType#createEnvironmentFactory(Project, TargetEnvironmentConfiguration)} method.
 * As a shortcut for retrieving factory of environment with particular configuration {@link TargetEnvironmentConfiguration#createEnvironmentFactory(Project)}
 * can be used
 * <p>
 * The creating of an environment happens in two phases:
 * 1. first, environment request should be created and fulfilled, see {@link this#createRequest()};
 * 2. then fulfilled request should be used for preparing target environment.
 * <p>
 * Usually, the client will look like this:
 * <code>
 * val factory = config.createEnvironmentFactory(project)
 * val request = factory.createRequest()
 * val commandLine = new TargetedCommandLine()
 * commandLine.setExePath("/bin/cat")
 * commandLine.addParameter(request.createUpload("/tmp/localInputFile.txt"))
 * factory.prepareRemoteEnvironment(request, progressIndicator).createProcess(commandLine, progressIndicator)
 * </code>
 * <p>
 * See the implementation for local machine: {@link com.intellij.execution.target.local.LocalTargetEnvironmentFactory}
 */
@ApiStatus.Experimental
public interface TargetEnvironmentFactory {
  /**
   * @return a configuration of target to create. Might be null for default settings.
   */
  @Nullable
  TargetEnvironmentConfiguration getTargetConfiguration();

  /**
   * @return a platform of the remote environment that factory builds.
   */
  @NotNull
  TargetPlatform getTargetPlatform();

  /**
   * @return a request for preparing target environment.
   */
  @NotNull
  TargetEnvironmentRequest createRequest();

  /**
   * Prepares the actual environment.
   * Might be time-consuming operation, so it's strongly recommended to support cancellation using passed {@link TargetEnvironmentAwareRunProfileState.TargetProgressIndicator}.
   * Throw localised exception to notify that preparation failed, and execution should not be proceeded.
   */
  @NotNull
  TargetEnvironment prepareRemoteEnvironment(@NotNull TargetEnvironmentRequest request,
                                             @NotNull TargetEnvironmentAwareRunProfileState.TargetProgressIndicator targetProgressIndicator);
}
