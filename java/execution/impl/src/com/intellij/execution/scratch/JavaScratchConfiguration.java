// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.DebuggingRunnerData;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.LanguageRuntimeType;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.execution.util.ProgramParametersUtil;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 */
public final class JavaScratchConfiguration extends ApplicationConfiguration {
  JavaScratchConfiguration(String name, @NotNull Project project, @NotNull ConfigurationFactory factory) {
    super(name, project, factory);
  }

  @Override
  public boolean isBuildProjectOnEmptyModuleList() {
    return false;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    JavaParametersUtil.checkAlternativeJRE(this);
    final String className = getMainClassName();
    if (className == null || className.isEmpty()) {
      throw new RuntimeConfigurationError(ExecutionBundle.message("no.main.class.specified.error.text"));
    }
    if (getScratchFileUrl() == null) {
      throw new RuntimeConfigurationError(JavaCompilerBundle.message("error.no.scratch.file.associated.with.configuration"));
    }
    if (getScratchVirtualFile() == null) {
      throw new RuntimeConfigurationError(JavaCompilerBundle.message("error.associated.scratch.file.not.found"));
    }
    ProgramParametersUtil.checkWorkingDirectoryExist(this, getProject(), getConfigurationModule().getModule());
    JavaRunConfigurationExtensionManager.checkConfigurationIsValid(this);
  }

  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final JavaCommandLineState state = new JavaApplicationCommandLineState<>(this, env) {
      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {
        final JavaParameters params = super.createJavaParameters();
        // After params are fully configured, additionally ensure JAVA_ENABLE_PREVIEW_PROPERTY is set,
        // because the scratch is compiled with this feature if it is supported by the JDK
        final Sdk jdk = params.getJdk();
        if (jdk != null) {
          final JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
          if (version != null && version.getMaxLanguageLevel().isPreview()) {
            final ParametersList vmOptions = params.getVMParametersList();
            if (!vmOptions.hasParameter(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY)) {
              vmOptions.add(JavaParameters.JAVA_ENABLE_PREVIEW_PROPERTY);
            }
          }
        }
        return params;
      }

      @Override
      protected void setupJavaParameters(@NotNull JavaParameters params) throws ExecutionException {
        super.setupJavaParameters(params);
        final File scrachesOutput = JavaScratchCompilationSupport.getScratchOutputDirectory(getProject());
        if (scrachesOutput != null) {
          params.getClassPath().addFirst(FileUtil.toCanonicalPath(scrachesOutput.getAbsolutePath()).replace('/', File.separatorChar));
        }
      }

      @Override
      protected @NotNull OSProcessHandler startProcess() throws ExecutionException {
        final OSProcessHandler handler = super.startProcess();
        if (getRunnerSettings() instanceof DebuggingRunnerData) {
          final VirtualFile vFile = getConfiguration().getScratchVirtualFile();
          if (vFile != null) {
            DebuggerManager.getInstance(getProject()).addDebugProcessListener(handler, new DebugProcessListener() {
              @Override
              public void processAttached(@NotNull DebugProcess process) {
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

  @Override
  public @Nullable PsiClass getMainClass() {
    // The class present in the scratch file is not part of the Project,
    // so the default implementation of this method from superclass won't do here.
    String mainClassName = getMainClassName();
    if (mainClassName == null) {
      throw new IllegalStateException("Main class name is not set");
    }
    VirtualFile scratchVirtualFile = getScratchVirtualFile();
    if (scratchVirtualFile == null) {
      throw new IllegalArgumentException("VirtualFile of the scratch file doesn't exist");
    }
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(scratchVirtualFile);
    if (!(psiFile instanceof PsiJavaFile psiJavaFile)) {
      throw new IllegalArgumentException("PsiJavaFile expected");
    }
    Ref<PsiClass> psiClassRef = Ref.create(null);
    psiJavaFile.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (mainClassName.equals(aClass.getQualifiedName())) {
          psiClassRef.set(aClass);
        }
        super.visitClass(aClass);
      }
    });
    return psiClassRef.get();
  }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new JavaScratchConfigurable(getProject());
  }

  public void setScratchFileUrl(String url) {
    getOptions().setScratchFileUrl(url);
  }

  public @Nullable String getScratchFileUrl() {
    return getOptions().getScratchFileUrl();
  }

  public @Nullable VirtualFile getScratchVirtualFile() {
    final String url = getScratchFileUrl();
    return url == null ? null : VirtualFileManager.getInstance().findFileByUrl(url);
  }

  @Override
  protected @NotNull JavaScratchConfigurationOptions getOptions() {
    return (JavaScratchConfigurationOptions)super.getOptions();
  }

  @Override
  public @Nullable LanguageRuntimeType<?> getDefaultLanguageRuntimeType() {
    return null;
  }

  @Override
  public @Nullable String getDefaultTargetName() {
    return null;
  }
}
