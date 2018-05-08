// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ConfigurationWithCommandLineShortener;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.psi.PsiJavaModule;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class ApplicationCommandLineState<T extends
  ModuleBasedConfiguration<JavaRunConfigurationModule> &
  CommonJavaRunConfigurationParameters &
  ConfigurationWithCommandLineShortener> extends BaseJavaApplicationCommandLineState<T> {

  public ApplicationCommandLineState(@NotNull final T configuration, final ExecutionEnvironment environment) {
    super(environment, configuration);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters params = new JavaParameters();
    T configuration = getConfiguration();
    params.setShortenCommandLine(configuration.getShortenCommandLine(), configuration.getProject());

    final JavaRunConfigurationModule module = myConfiguration.getConfigurationModule();
    final String jreHome = myConfiguration.isAlternativeJrePathEnabled() ? myConfiguration.getAlternativeJrePath() : null;
    if (module.getModule() != null) {
      DumbService.getInstance(module.getProject()).runWithAlternativeResolveEnabled(() -> {
        int classPathType = JavaParametersUtil.getClasspathType(module, myConfiguration.getRunClass(), false,
                                                                isProvidedScopeIncluded());
        JavaParametersUtil.configureModule(module, params, classPathType, jreHome);
      });
    }
    else {
      JavaParametersUtil.configureProject(module.getProject(), params, JavaParameters.JDK_AND_CLASSES_AND_TESTS, jreHome);
    }

    params.setMainClass(myConfiguration.getRunClass());

    setupJavaParameters(params);

    setupModulePath(params, module);

    return params;
  }

  @Override
  protected GeneralCommandLine createCommandLine() throws ExecutionException {
    GeneralCommandLine line = super.createCommandLine();
    Map<String, String> content = line.getUserData(JdkUtil.COMMAND_LINE_CONTENT);
    if (content != null) {
      content.forEach((key, value) -> addConsoleFilters(new ArgumentFileFilter(key, value)));
    }
    return line;
  }

  @NotNull
  @Override
  protected OSProcessHandler startProcess() throws ExecutionException {
    OSProcessHandler processHandler = super.startProcess();
    if (processHandler instanceof KillableProcessHandler && DebuggerSettings.getInstance().KILL_PROCESS_IMMEDIATELY) {
      ((KillableProcessHandler)processHandler).setShouldKillProcessSoftly(false);
    }
    return processHandler;
  }

  private static void setupModulePath(JavaParameters params, JavaRunConfigurationModule module) {
    if (JavaSdkUtil.isJdkAtLeast(params.getJdk(), JavaSdkVersion.JDK_1_9)) {
      PsiJavaModule mainModule = DumbService.getInstance(module.getProject()).computeWithAlternativeResolveEnabled(
        () -> JavaModuleGraphUtil.findDescriptorByElement(module.findClass(params.getMainClass())));
      if (mainModule != null) {
        params.setModuleName(mainModule.getName());
        PathsList classPath = params.getClassPath(), modulePath = params.getModulePath();
        modulePath.addAll(classPath.getPathList());
        classPath.clear();
      }
    }
  }

  protected abstract boolean isProvidedScopeIncluded();
}