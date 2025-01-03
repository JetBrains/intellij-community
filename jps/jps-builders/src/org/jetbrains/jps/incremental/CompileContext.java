// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public interface CompileContext extends UserDataHolder, MessageHandler {
  @NotNull ProjectDescriptor getProjectDescriptor();

  @NotNull CompileScope getScope();

  /**
   * @deprecated use {@link JavaBuilderUtil#isForcedRecompilationAllJavaModules(CompileContext)} for java-related usages
   */
  @Deprecated
  default boolean isProjectRebuild() {
    return JavaBuilderUtil.isForcedRecompilationAllJavaModules(getScope());
  }

  @Nullable
  String getBuilderParameter(String paramName);

  /**
   * Registers a listener which will receive events about files which are created, modified or deleted by the build process.
   * To ensure that no events are lost, this method may be called in {@link Builder#buildStarted}'s implementation.
   */
  void addBuildListener(@NotNull BuildListener listener);

  void removeBuildListener(@NotNull BuildListener listener);

  boolean shouldDifferentiate(@NotNull ModuleChunk chunk);

  @NotNull CanceledStatus getCancelStatus();

  default boolean isCanceled() {
    return getCancelStatus().isCanceled();
  }

  void checkCanceled() throws ProjectBuildException;

  @NotNull BuildLoggingManager getLoggingManager();

  void setDone(float done);

  long getCompilationStartStamp(BuildTarget<?> target);

  void setCompilationStartStamp(@NotNull Collection<? extends BuildTarget<?>> target, long stamp);

  void markNonIncremental(@NotNull ModuleBuildTarget target);

  void clearNonIncrementalMark(@NotNull ModuleBuildTarget target);
}
