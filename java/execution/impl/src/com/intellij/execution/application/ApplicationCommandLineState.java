// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ConfigurationWithCommandLineShortener;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.light.LightJavaModule;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class ApplicationCommandLineState<T extends
  ModuleBasedConfiguration<JavaRunConfigurationModule, Element> &
  CommonJavaRunConfigurationParameters &
  ConfigurationWithCommandLineShortener> extends BaseJavaApplicationCommandLineState<T> {

  public ApplicationCommandLineState(@NotNull final T configuration, final ExecutionEnvironment environment) {
    super(environment, configuration);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters params = new JavaParameters();
    T configuration = getConfiguration();

    params.setMainClass(ReadAction.compute(() -> myConfiguration.getRunClass()));
    setupJavaParameters(params);

    final JavaRunConfigurationModule module = myConfiguration.getConfigurationModule();
    ReadAction.run(() -> {
      final String jreHome = getTargetEnvironmentRequest() == null && myConfiguration.isAlternativeJrePathEnabled() ? myConfiguration.getAlternativeJrePath() : null;
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
    });

    setupModulePath(params, module);

    params.setShortenCommandLine(configuration.getShortenCommandLine(), configuration.getProject());

    return params;
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
      DumbService dumbService = DumbService.getInstance(module.getProject());
      PsiJavaModule mainModule = ReadAction.compute(() -> dumbService.computeWithAlternativeResolveEnabled(
        () -> JavaModuleGraphUtil.findDescriptorByElement(module.findClass(params.getMainClass()))));
      if (mainModule != null) {
        boolean inLibrary = mainModule instanceof PsiCompiledElement || mainModule instanceof LightJavaModule;
        if (!inLibrary || ReadAction.compute(() -> JavaModuleGraphUtil.findNonAutomaticDescriptorByModule(module.getModule(), false)) != null) {
          params.setModuleName(ReadAction.compute(() -> mainModule.getName()));
          dumbService.runReadActionInSmartMode(() -> JavaParametersUtil.putDependenciesOnModulePath(params, mainModule, false));
        }
      }
    }
  }

  protected abstract boolean isProvidedScopeIncluded();
}