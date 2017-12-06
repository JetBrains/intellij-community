/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public interface CompileContext extends UserDataHolder, MessageHandler {
  ProjectDescriptor getProjectDescriptor();

  CompileScope getScope();

  /**
   * @deprecated use {@link org.jetbrains.jps.builders.java.JavaBuilderUtil#isCompileJavaIncrementally(CompileContext)} for java-related usages
   */
  boolean isMake();

  /**
   * @deprecated use {@link org.jetbrains.jps.builders.java.JavaBuilderUtil#isForcedRecompilationAllJavaModules(CompileContext)} for java-related usages
   */
  boolean isProjectRebuild();

  @Nullable
  String getBuilderParameter(String paramName);

  void addBuildListener(BuildListener listener);

  void removeBuildListener(BuildListener listener);

  boolean shouldDifferentiate(ModuleChunk chunk);

  CanceledStatus getCancelStatus();

  void checkCanceled() throws ProjectBuildException;

  BuildLoggingManager getLoggingManager();

  void setDone(float done);

  long getCompilationStartStamp(BuildTarget<?> target);

  void setCompilationStartStamp(Collection<BuildTarget<?>> target, long stamp);

  void markNonIncremental(ModuleBuildTarget target);

  void clearNonIncrementalMark(ModuleBuildTarget target);
}
