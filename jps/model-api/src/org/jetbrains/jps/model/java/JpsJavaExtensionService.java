// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.ApiStatus;
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
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

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

  public abstract @NotNull JpsProductionModuleOutputPackagingElement createProductionModuleOutput(@NotNull JpsModuleReference moduleReference);

  public abstract @NotNull JpsProductionModuleSourcePackagingElement createProductionModuleSource(@NotNull JpsModuleReference moduleReference);

  public abstract @NotNull JpsTestModuleOutputPackagingElement createTestModuleOutput(@NotNull JpsModuleReference moduleReference);

  public abstract JpsJavaDependenciesEnumerator enumerateDependencies(Collection<JpsModule> modules);

  protected abstract JpsJavaDependenciesEnumerator enumerateDependencies(JpsProject project);

  protected abstract JpsJavaDependenciesEnumerator enumerateDependencies(JpsModule module);

  @ApiStatus.Internal
  public abstract @NotNull JpsJavaProjectExtension getOrCreateProjectExtension(@NotNull JpsProject project);

  public abstract @Nullable JpsJavaProjectExtension getProjectExtension(@NotNull JpsProject project);

  @ApiStatus.Internal
  public abstract @NotNull JpsJavaModuleExtension getOrCreateModuleExtension(@NotNull JpsModule module);

  public abstract @Nullable JpsJavaModuleExtension getModuleExtension(@NotNull JpsModule module);

  @ApiStatus.Internal
  public abstract @NotNull JpsJavaDependencyExtension getOrCreateDependencyExtension(@NotNull JpsDependencyElement dependency);

  public abstract @Nullable JpsJavaDependencyExtension getDependencyExtension(@NotNull JpsDependencyElement dependency);

  @ApiStatus.Internal
  public abstract @Nullable ExplodedDirectoryModuleExtension getExplodedDirectoryExtension(@NotNull JpsModule module);

  @ApiStatus.Internal
  public abstract @NotNull ExplodedDirectoryModuleExtension getOrCreateExplodedDirectoryExtension(@NotNull JpsModule module);

  public abstract @NotNull List<JpsDependencyElement> getDependencies(JpsModule module, JpsJavaClasspathKind classpathKind, boolean exportedOnly);

  public abstract @Nullable LanguageLevel getLanguageLevel(JpsModule module);

  public abstract @Nullable String getOutputUrl(JpsModule module, boolean forTests);

  /**
   * Use {@link #getOutputDirectoryPath} instead.
   */
  public abstract @Nullable File getOutputDirectory(JpsModule module, boolean forTests);

  /**
   * Returns a path to the directory where class files and resources for the module will be placed after compilation or {@code null} if
   * the path isn't specified.
   */
  public abstract @Nullable Path getOutputDirectoryPath(JpsModule module, boolean forTests);

  /**
   * Returns the path to an existing file under {@code root} for which the full relative path is equal to {@code relativePath}, 
   * taking the package prefix into account.
   * <br/>
   * E.g., if the package prefix is 'foo', and there is 'bar/Baz.java' file under {@code root}, 
   * {@code findSourceFile(root, "foo/bar/Baz.java")} will return path to it.
   * 
   * @param relativePath relative path to the file with '/' as a separator 
   */
  public abstract @Nullable Path findSourceFile(@NotNull JpsModuleSourceRoot root, @NotNull String relativePath);

  @ApiStatus.Internal
  public abstract JpsTypedLibrary<JpsSdk<JpsDummyElement>> addJavaSdk(@NotNull JpsGlobal global, @NotNull String name, @NotNull String homePath);

  public abstract @NotNull JpsJavaCompilerConfiguration getCompilerConfiguration(@NotNull JpsProject project);

  public abstract @Nullable JpsTestModuleProperties getTestModuleProperties(@NotNull JpsModule module);

  @ApiStatus.Internal
  public abstract void setTestModuleProperties(@NotNull JpsModule module, @NotNull JpsModuleReference productionModuleReference);

  @ApiStatus.Internal
  public abstract @NotNull JpsSdkReference<JpsDummyElement> createWrappedJavaSdkReference(@NotNull JpsJavaSdkTypeWrapper sdkType,
                                                                                          @NotNull JpsSdkReference<?> wrapperReference);

  public abstract @NotNull JpsApplicationRunConfigurationProperties createRunConfigurationProperties(JpsApplicationRunConfigurationState state);

  public abstract @NotNull JavaSourceRootProperties createSourceRootProperties(@NotNull String packagePrefix, boolean isGenerated);

  public abstract @NotNull JavaSourceRootProperties createSourceRootProperties(@NotNull String packagePrefix);

  public abstract @NotNull JavaResourceRootProperties createResourceRootProperties(@NotNull String relativeOutputPath, boolean forGeneratedResource);

  public abstract @NotNull JavaModuleIndex getJavaModuleIndex(@NotNull JpsProject project);
}