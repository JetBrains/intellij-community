/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.scratch;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 */
public class JavaScratchConfiguration extends ApplicationConfiguration {
  public int SCRATCH_FILE_ID;

  protected JavaScratchConfiguration(String name, Project project, ConfigurationFactory factory) {
    super(name, project, factory);
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(this);
    final String className = getMainClassName();
    if (className == null || className.length() == 0) {
      throw new RuntimeConfigurationError(ExecutionBundle.message("no.main.class.specified.error.text"));
    }
    if (SCRATCH_FILE_ID <= 0) {
      throw new RuntimeConfigurationError("No scratch file associated with configuration");
    }
    if (getScratchVirtualFile() == null) {
      throw new RuntimeConfigurationError("Associated scratch file not found");
    }
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), getConfigurationModule().getModule());
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final JavaCommandLineState state = new JavaApplicationCommandLineState<JavaScratchConfiguration>(this, env) {
      @Override
      protected void setupJavaParameters(JavaParameters params) throws ExecutionException {
        super.setupJavaParameters(params);
        final File scrachesOutput = JavaScratchCompilationSupport.getScratchOutputDirectory(getProject());
        if (scrachesOutput != null) {
          params.getClassPath().addFirst(FileUtil.toCanonicalPath(scrachesOutput.getAbsolutePath()).replace('/', File.separatorChar));
        }
      }

      @NotNull
      @Override
      protected OSProcessHandler startProcess() throws ExecutionException {
        final OSProcessHandler handler = super.startProcess();
        if (getRunnerSettings() instanceof DebuggingRunnerData) {
          final VirtualFile vFile = getConfiguration().getScratchVirtualFile();
          if (vFile != null) {
            DebuggerManager.getInstance(getProject()).addDebugProcessListener(handler, new DebugProcessListener() {
              @Override
              public void processAttached(DebugProcess process) {
                if (vFile.isValid()) {
                  process.appendPositionManager(new JavaScratchPositionManager((DebugProcessImpl)process, vFile));
                }
                process.removeDebugProcessListener(this);
              }
            });
          }
        }
        return handler;
      }
    };
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject(), getConfigurationModule().getSearchScope()));
    return state;
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new JavaScratchConfigurable(getProject());
  }

  @Override
  public boolean isCompileBeforeLaunchAddedByDefault() {
    return true;
  }

  @Nullable
  public String getScratchFileUrl() {
    final VirtualFile vFile = getScratchVirtualFile();
    return vFile != null? vFile.getUrl() : null;
  }

  @Nullable
  public VirtualFile getScratchVirtualFile() {
    final int id = SCRATCH_FILE_ID;
    if (id <= 0) {
      return null;
    }
    return ManagingFS.getInstance().findFileById(id);
  }
}
