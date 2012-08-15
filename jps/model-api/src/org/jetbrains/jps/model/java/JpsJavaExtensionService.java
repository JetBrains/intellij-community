package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsJavaExtensionService {
  @NotNull
  public abstract JpsProductionModuleOutputPackagingElement createProductionModuleOutput(@NotNull JpsModuleReference moduleReference);

  @NotNull
  public abstract JpsTestModuleOutputPackagingElement createTestModuleOutput(@NotNull JpsModuleReference moduleReference);

  public static JpsJavaExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsJavaExtensionService.class);
  }

  public static JpsJavaDependenciesEnumerator dependencies(JpsModule module) {
    return getInstance().enumerateDependencies(module);
  }

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
  public abstract String getSourcePrefix(JpsModule module, String rootUrl);

  public abstract JpsTypedLibrary<JpsSdk<JpsDummyElement>> addJavaSdk(@NotNull JpsGlobal global, @NotNull String name,
                                                                      @NotNull String homePath);
}
