// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationProperties;
import org.jetbrains.jps.model.java.runConfiguration.JpsApplicationRunConfigurationState;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.model.module.JpsTestModuleProperties;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public abstract class JpsJavaExtensionService {
  public static JpsJavaExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsJavaExtensionService.class);
  }

  public static JpsJavaDependenciesEnumerator dependencies(JpsModule module) {
    return getInstance().enumerateDependencies(module);
  }

  public static JpsJavaDependenciesEnumerator dependencies(JpsProject project) {
    return getInstance().enumerateDependencies(project);
  }

  @NotNull
  public abstract JpsProductionModuleOutputPackagingElement createProductionModuleOutput(@NotNull JpsModuleReference moduleReference);

  @NotNull
  public abstract JpsTestModuleOutputPackagingElement createTestModuleOutput(@NotNull JpsModuleReference moduleReference);

  public abstract JpsJavaDependenciesEnumerator enumerateDependencies(Collection<JpsModule> modules);

  protected abstract JpsJavaDependenciesEnumerator enumerateDependencies(JpsProject project);

  protected abstract JpsJavaDependenciesEnumerator enumerateDependencies(JpsModule module);

  @NotNull
  public abstract JpsJavaProjectExtension getOrCreateProjectExtension(@NotNull JpsProject project);

  @Nullable
  public abstract JpsJavaProjectExtension getProjectExtension(@NotNull JpsProject project);

  @NotNull
  public abstract JpsJavaModuleExtension getOrCreateModuleExtension(@NotNull JpsModule module);

  @Nullable
  public abstract JpsJavaModuleExtension getModuleExtension(@NotNull JpsModule module);

  @NotNull
  public abstract JpsJavaDependencyExtension getOrCreateDependencyExtension(@NotNull JpsDependencyElement dependency);

  @Nullable
  public abstract JpsJavaDependencyExtension getDependencyExtension(@NotNull JpsDependencyElement dependency);

  @Nullable
  public abstract ExplodedDirectoryModuleExtension getExplodedDirectoryExtension(@NotNull JpsModule module);

  @NotNull
  public abstract ExplodedDirectoryModuleExtension getOrCreateExplodedDirectoryExtension(@NotNull JpsModule module);

  @NotNull
  public abstract List<JpsDependencyElement> getDependencies(JpsModule module, JpsJavaClasspathKind classpathKind, boolean exportedOnly);

  @Nullable
  public abstract LanguageLevel getLanguageLevel(JpsModule module);

  @Nullable
  public abstract String getOutputUrl(JpsModule module, boolean forTests);

  @Nullable
  public abstract File getOutputDirectory(JpsModule module, boolean forTests);

  public abstract JpsTypedLibrary<JpsSdk<JpsDummyElement>> addJavaSdk(@NotNull JpsGlobal global, @NotNull String name, @NotNull String homePath);

  @Nullable
  public abstract JpsJavaCompilerConfiguration getCompilerConfiguration(@NotNull JpsProject project);

  @NotNull
  public abstract JpsJavaCompilerConfiguration getOrCreateCompilerConfiguration(@NotNull JpsProject project);

  @Nullable
  public abstract JpsTestModuleProperties getTestModuleProperties(@NotNull JpsModule module);

  public abstract void setTestModuleProperties(@NotNull JpsModule module, @NotNull JpsModuleReference productionModuleReference);

  @NotNull
  public abstract JpsSdkReference<JpsDummyElement> createWrappedJavaSdkReference(@NotNull JpsJavaSdkTypeWrapper sdkType,
                                                                                 @NotNull JpsSdkReference<?> wrapperReference);

  @NotNull
  public abstract JpsApplicationRunConfigurationProperties createRunConfigurationProperties(JpsApplicationRunConfigurationState state);

  @NotNull
  public abstract JavaSourceRootProperties createSourceRootProperties(@NotNull String packagePrefix, boolean isGenerated);

  @NotNull
  public abstract JavaSourceRootProperties createSourceRootProperties(@NotNull String packagePrefix);

  @NotNull
  public abstract JavaResourceRootProperties createResourceRootProperties(@NotNull String relativeOutputPath, boolean forGeneratedResource);

  @NotNull
  public abstract JavaModuleIndex getJavaModuleIndex(@NotNull JpsProject project);
}