// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author Vladislav.Soroka
 */
public class ProjectTaskContext extends UserDataHolderBase {
  private final @Nullable Object mySessionId;
  private final @Nullable RunConfiguration myRunConfiguration;
  private final boolean myAutoRun;
  private final MultiMap<String, String> myGeneratedFiles;
  private final List<Supplier<? extends Collection<String>>> myDirtyOutputPaths;
  private volatile boolean myCollectGeneratedFiles;

  public ProjectTaskContext() {
    this(null, null, false);
  }

  public ProjectTaskContext(boolean autoRun) {
    this(null, null, autoRun);
  }

  public ProjectTaskContext(@Nullable Object sessionId) {
    this(sessionId, null, false);
  }

  public ProjectTaskContext(@Nullable Object sessionId, @Nullable RunConfiguration runConfiguration) {
    this(sessionId, runConfiguration, false);
  }

  public ProjectTaskContext(@Nullable Object sessionId, @Nullable RunConfiguration runConfiguration, boolean autoRun) {
    mySessionId = sessionId;
    myRunConfiguration = runConfiguration;
    myAutoRun = autoRun;
    myGeneratedFiles = MultiMap.createConcurrentSet();
    myDirtyOutputPaths = ContainerUtil.createConcurrentList();
  }

  public @Nullable Object getSessionId() {
    return mySessionId;
  }

  public @Nullable RunConfiguration getRunConfiguration() {
    return myRunConfiguration;
  }

  /**
   * @return true indicates that the task was started automatically, e.g. resources compilation on frame deactivation
   */
  public boolean isAutoRun() {
    return myAutoRun;
  }

  @ApiStatus.Experimental
  public void enableCollectionOfGeneratedFiles() {
    myCollectGeneratedFiles = true;
  }

  @ApiStatus.Experimental
  public boolean isCollectionOfGeneratedFilesEnabled() {
    return myCollectGeneratedFiles;
  }

  /**
   * Returns roots of the files generated during the task session.
   * Note, generated files collecting is disabled by default.
   * It can be requested using the {@link #enableCollectionOfGeneratedFiles()} method by the task initiator, see {@link ProjectTaskManager#run(ProjectTaskContext, ProjectTask)}.
   * Or using the {@link ProjectTaskListener#started(ProjectTaskContext)} event.
   */
  @ApiStatus.Experimental
  public @NotNull @Unmodifiable Collection<String> getGeneratedFilesRoots() {
    return myGeneratedFiles.keySet();
  }

  /**
   * Returns files generated during the task session in the specified root.
   * Note, generated files collecting is disabled by default.
   * It can be requested using the {@link #enableCollectionOfGeneratedFiles()} method by the task initiator, see {@link ProjectTaskManager#run(ProjectTaskContext, ProjectTask)}.
   * Or using the {@link ProjectTaskListener#started(ProjectTaskContext)} event.
   */
  @ApiStatus.Experimental
  public @NotNull @Unmodifiable Collection<String> getGeneratedFilesRelativePaths(@NotNull String root) {
    return myGeneratedFiles.get(root);
  }

  /**
   * This method isn't supposed to be used directly.
   * {@link ProjectTaskRunner}s can use it to report information about generated files during some task execution.
   *
   * @param root the root directory of the generated file
   * @param relativePath the generated file relative path with regard to the root
   */
  @ApiStatus.Experimental
  public void fileGenerated(@NotNull String root, @NotNull String relativePath) {
    if (myCollectGeneratedFiles) {
      myGeneratedFiles.putValue(root, relativePath);
    }
  }

  /**
   * This method isn't supposed to be used directly.
   * {@link ProjectTaskRunner}s can use it to report output paths of generated files produced during some task execution.
   * <p>
   * Note, generated files collecting is disabled by default.
   * It can be requested using the {@link #enableCollectionOfGeneratedFiles()} method by the task initiator, see {@link ProjectTaskManager#run(ProjectTaskContext, ProjectTask)}.
   * Or using the {@link ProjectTaskListener#started(ProjectTaskContext)} event.
   * <p>
   * The method should be used ONLY if the {@link ProjectTaskRunner} doesn't support {@link #fileGenerated} events.
   */
  @ApiStatus.Experimental
  public void addDirtyOutputPathsProvider(@NotNull Supplier<? extends Collection<String>> outputPathsProvider) {
    if (myCollectGeneratedFiles) {
      myDirtyOutputPaths.add(outputPathsProvider);
    }
  }

  /**
   * Provides output paths that can be used for generated files by some tasks.
   * The intended usage is to scan those directories for modified files.
   * <p>
   * Can be useful for {@link ProjectTaskRunner}s which doesn't support {@link #fileGenerated} events.
   */
  @ApiStatus.OverrideOnly
  public Optional<Stream<String>> getDirtyOutputPaths() {
    return myDirtyOutputPaths.isEmpty() ? Optional.empty() :
           Optional.of(myDirtyOutputPaths.stream().map(supplier -> supplier.get()).flatMap(Collection::stream).distinct());
  }

  public <T> ProjectTaskContext withUserData(@NotNull Key<T> key, @Nullable T value) {
    putUserData(key, value);
    return this;
  }
}
