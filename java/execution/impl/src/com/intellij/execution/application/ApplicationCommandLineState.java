// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.CantRunException;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ConfigurationWithCommandLineShortener;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.java.JavaPluginDisposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.util.ExceptionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public abstract class ApplicationCommandLineState<T extends
  ModuleBasedConfiguration<JavaRunConfigurationModule, Element> &
  CommonJavaRunConfigurationParameters &
  ConfigurationWithCommandLineShortener> extends BaseJavaApplicationCommandLineState<T> {

  public ApplicationCommandLineState(final @NotNull T configuration, final ExecutionEnvironment environment) {
    super(environment, configuration);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters params = new JavaParameters();
    T configuration = getConfiguration();

    JavaPluginDisposable expireReadsWith = JavaPluginDisposable.getInstance(configuration.getProject());

    String mainClass = ReadAction.nonBlocking(() -> configuration.getRunClass())
      .expireWith(expireReadsWith)
      .executeSynchronously();

    params.setMainClass(mainClass);
    try {
      JavaParametersUtil.configureConfiguration(params, configuration);
    }
    catch (ProgramParametersConfigurator.ParametersConfiguratorException e) {
      throw new ExecutionException(e);
    }

    final JavaRunConfigurationModule module = configuration.getConfigurationModule();
    try {
      ReadAction.nonBlocking(() -> {
        final String jreHome = getTargetEnvironmentRequest() == null && configuration.isAlternativeJrePathEnabled() ? configuration.getAlternativeJrePath() : null;
        if (module.getModule() != null) {
          DumbService.getInstance(module.getProject()).runWithAlternativeResolveEnabled(() -> {
            if (mainClass == null) {
              throw new CantRunException(ExecutionBundle.message("no.main.class.defined.error.message"));
            }
            int classPathType = JavaParametersUtil.getClasspathType(module, mainClass, false,
                                                                    isProvidedScopeIncluded());
            // todo effects must be applied in non-cancellable section
            JavaParametersUtil.configureModule(module, params, classPathType, jreHome);
          });
        }
        else {
          // todo effects must be applied in non-cancellable section
          JavaParametersUtil.configureProject(module.getProject(), params, JavaParameters.JDK_AND_CLASSES_AND_TESTS, jreHome);
        }
        return null;
      })
        .expireWith(expireReadsWith)
        .executeSynchronously();
    }
    catch (Exception e) {
      ExecutionException executionException = ExceptionUtil.findCause(e, ExecutionException.class);
      if (executionException != null) {
        throw executionException;
      }
      else {
        throw e;
      }
    }

    setupModulePath(params, module);

    params.setShortenCommandLine(configuration.getShortenCommandLine(), configuration.getProject());

    setupJavaParameters(params);

    return params;
  }

  @Override
  protected @NotNull OSProcessHandler startProcess() throws ExecutionException {
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
          boolean isExcluded = !(mainModule instanceof LightJavaModule) && CompilerConfiguration.getInstance(module.getProject())
            .isExcludedFromCompilation(mainModule.getContainingFile().getVirtualFile());
          if(!isExcluded) {
            params.setModuleName(ReadAction.compute(() -> mainModule.getName()));
            dumbService.runReadActionInSmartMode(() -> JavaParametersUtil.putDependenciesOnModulePath(params, mainModule, false));
          }
        }
      }
    }
  }

  protected abstract boolean isProvidedScopeIncluded();
}