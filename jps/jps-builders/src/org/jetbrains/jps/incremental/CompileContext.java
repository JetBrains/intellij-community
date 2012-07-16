package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

/**
 * @author Eugene Zhuravlev
 *         Date: 7/8/12
 */
public interface CompileContext extends UserDataHolder, MessageHandler {
  ProjectDescriptor getProjectDescriptor();

  ProjectPaths getProjectPaths();

  CompileScope getScope();

  boolean isMake();

  boolean isProjectRebuild();

  @Nullable
  String getBuilderParameter(String paramName);

  void addBuildListener(BuildListener listener);

  void removeBuildListener(BuildListener listener);

  boolean isCompilingTests();

  @NotNull
  AnnotationProcessingProfile getAnnotationProcessingProfile(Module module);


  boolean shouldDifferentiate(ModuleChunk chunk, boolean forTests);

  CanceledStatus getCancelStatus();

  void checkCanceled() throws ProjectBuildException;

  BuildLoggingManager getLoggingManager();

  void setDone(float done);

  ProjectChunks getTestChunks();

  ProjectChunks getProductionChunks();

  long getCompilationStartStamp();

  void markNonIncremental(Module module);

  void clearNonIncrementalMark(Module module);
}
