package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.module.JpsModule;

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

  @NotNull
  ProcessorConfigProfile getAnnotationProcessingProfile(JpsModule module);


  boolean shouldDifferentiate(ModuleChunk chunk);

  CanceledStatus getCancelStatus();

  void checkCanceled() throws ProjectBuildException;

  BuildLoggingManager getLoggingManager();

  void setDone(float done);

  long getCompilationStartStamp();

  void updateCompilationStartStamp();

  void markNonIncremental(ModuleBuildTarget target);

  void clearNonIncrementalMark(ModuleBuildTarget target);
}
