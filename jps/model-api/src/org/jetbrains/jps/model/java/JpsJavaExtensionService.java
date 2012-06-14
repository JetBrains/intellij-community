package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author nik
 */
public abstract class JpsJavaExtensionService {
  public static JpsJavaExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsJavaExtensionService.class);
  }

  @NotNull
  public abstract JpsJavaModuleExtension getOrCreateModuleExtension(@NotNull JpsModule module);

  @NotNull
  public abstract JpsJavaDependencyExtension getOrCreateDependencyExtension(@NotNull JpsDependencyElement dependency);

  @NotNull
  public abstract JpsElementKind<? extends JpsJavaModuleExtension> getModuleExtensionKind();

  @NotNull
  public abstract JpsElementKind<? extends JpsJavaDependencyExtension> getDependencyExtensionKind();

  @NotNull
  public abstract ExplodedDirectoryModuleExtension getOrCreateExplodedDirectoryExtension(@NotNull JpsModule module);
}
