// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import java.util.List;

public abstract class CompilerConfiguration {
  public static CompilerConfiguration getInstance(@NotNull Project project) {
    return project.getService(CompilerConfiguration.class);
  }

  public abstract int getBuildProcessHeapSize(int javacPreferredHeapSize);
  public abstract void setBuildProcessHeapSize(int size);

  public abstract String getBuildProcessVMOptions();
  public abstract void setBuildProcessVMOptions(String options);

  /**
   * Specifies whether '--release' cross-compilation option should be used. Applicable to jdk 9 and later
   */
  public abstract boolean useReleaseOption();
  public abstract void setUseReleaseOption(boolean useReleaseOption);

  public abstract @Nullable String getProjectBytecodeTarget();
  public abstract void setProjectBytecodeTarget(String level);

  public abstract boolean isParallelCompilationEnabled();

  /**
   * Explicitly set parallel value property. The value will be written to user-local settings file (workspace.xml)
   * This value will override any value set in shared settings.
   * @deprecated use {@link #setParallelCompilationOption(ParallelCompilationOption)} instead
   */
  @Deprecated
  public abstract void setParallelCompilationEnabled(boolean enabled);

  public abstract @NotNull ParallelCompilationOption getParallelCompilationOption();
  public abstract void setParallelCompilationOption(@NotNull ParallelCompilationOption option);

  public abstract @Nullable String getBytecodeTargetLevel(Module module);
  public abstract void setBytecodeTargetLevel(Module module, String level);

  /**
   * Returns the project default additional compiler options.
   * These options are used if no additional compiler options are defined that are applicable to a module.
   */
  public abstract @NotNull List<String> getAdditionalOptions();

  /**
   * Sets the project default additional compiler options.
   * These options are used if no additional compiler options are defined that are applicable to a module.
   */
  public abstract void setAdditionalOptions(@NotNull List<String> options);

  /**
   * Returns additional compiler options applicable to the given module.
   * Otherwise, it returns the project default additional compiler option.
   */
  public abstract @NotNull List<String> getAdditionalOptions(@NotNull Module module);

  /**
   * Sets additional compiler options applicable to the given module.
   */
  public abstract void setAdditionalOptions(@NotNull Module module, @NotNull List<String> options);

  /**
   * Removes additional compiler options applicable to the given module.
   * It means that the project default additional compiler options will be used for the given module.
   */
  public abstract void removeAdditionalOptions(@NotNull Module module);

  public abstract @NotNull AnnotationProcessingConfiguration getAnnotationProcessingConfiguration(Module module);

  /**
   * Adds a new empty annotation processing profile with the given name and returns the created instance.
   */
  public abstract @NotNull ProcessorConfigProfile addNewProcessorProfile(@NotNull String profileName);

  /**
   * Returns true if at least one enabled annotation processing profile exists.
   */
  public abstract boolean isAnnotationProcessorsEnabled();

  public abstract boolean isExcludedFromCompilation(VirtualFile virtualFile);
  public abstract boolean isResourceFile(VirtualFile virtualFile);
  public abstract boolean isResourceFile(String path);
  public abstract boolean isCompilableResourceFile(Project project, VirtualFile file);

  public abstract void addResourceFilePattern(String namePattern) throws MalformedPatternException;

  public abstract boolean isAddNotNullAssertions();
  public abstract void setAddNotNullAssertions(boolean enabled);

  public abstract ExcludesConfiguration getExcludedEntriesConfiguration();
}