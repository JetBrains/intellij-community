// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.compiler;

import com.intellij.build.BuildWorkspaceConfiguration;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "CompilerWorkspaceConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class CompilerWorkspaceConfiguration implements PersistentStateComponent<CompilerWorkspaceConfiguration> {
  private static final Logger LOG = Logger.getInstance(CompilerWorkspaceConfiguration.class);

  static {
    LOG.info("Available processors: " + Runtime.getRuntime().availableProcessors());
  }

  public boolean AUTO_SHOW_ERRORS_IN_EDITOR = true;
  public boolean DISPLAY_NOTIFICATION_POPUP = true;
  public boolean CLEAR_OUTPUT_DIRECTORY = true;
  public boolean MAKE_PROJECT_ON_SAVE = false; // until we fix problems with several open projects (IDEA-104064), daemon slowness (IDEA-104666)

  /**
   * @deprecated use {@link CompilerConfiguration#isParallelCompilationEnabled()}
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public Boolean PARALLEL_COMPILATION = null;

  public int COMPILER_PROCESS_HEAP_SIZE = 0;
  public String COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS = "";
  public boolean REBUILD_ON_DEPENDENCY_CHANGE = true;
  public boolean COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT = true;

  public static CompilerWorkspaceConfiguration getInstance(Project project) {
    return project.getService(CompilerWorkspaceConfiguration.class);
  }

  @Override
  public CompilerWorkspaceConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull CompilerWorkspaceConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean allowAutoMakeWhileRunningApplication() {
    return AdvancedSettings.getBoolean("compiler.automake.allow.when.app.running");
  }

  @ApiStatus.Internal
  static final class JavaBuildWorkspaceConfiguration implements BuildWorkspaceConfiguration {
    private final @NotNull Project myProject;

    JavaBuildWorkspaceConfiguration(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public boolean isShowFirstErrorInEditor() {
      return getInstance(myProject).AUTO_SHOW_ERRORS_IN_EDITOR;
    }
  }
}
