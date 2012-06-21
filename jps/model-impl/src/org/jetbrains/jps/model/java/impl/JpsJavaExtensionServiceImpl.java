package org.jetbrains.jps.model.java.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementKind;
import org.jetbrains.jps.model.java.ExplodedDirectoryModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsJavaExtensionServiceImpl extends JpsJavaExtensionService {
  @NotNull
  @Override
  public JpsJavaModuleExtension getOrCreateModuleExtension(@NotNull JpsModule module) {
    JpsJavaModuleExtension child = module.getContainer().getChild(JavaModuleExtensionKind.INSTANCE);
    if (child == null) {
      child = module.getContainer().setChild(JavaModuleExtensionKind.INSTANCE);
    }
    return child;
  }

  @NotNull
  @Override
  public JpsJavaDependencyExtension getOrCreateDependencyExtension(@NotNull JpsDependencyElement dependency) {
    JpsJavaDependencyExtension extension = dependency.getContainer().getChild(JpsJavaDependencyExtensionKind.INSTANCE);
    if (extension == null) {
      extension = dependency.getContainer().setChild(JpsJavaDependencyExtensionKind.INSTANCE);
    }
    return extension;
  }

  @NotNull
  @Override
  public JpsElementKind<JpsJavaModuleExtension> getModuleExtensionKind() {
    return JavaModuleExtensionKind.INSTANCE;
  }

  @NotNull
  @Override
  public JpsElementKind<JpsJavaDependencyExtension> getDependencyExtensionKind() {
    return JpsJavaDependencyExtensionKind.INSTANCE;
  }

  @Override
  @NotNull
  public ExplodedDirectoryModuleExtension getOrCreateExplodedDirectoryExtension(@NotNull JpsModule module) {
    ExplodedDirectoryModuleExtension extension = module.getContainer().getChild(ExplodedDirectoryModuleExtensionImpl.KIND);
    if (extension == null) {
      extension = module.getContainer().setChild(ExplodedDirectoryModuleExtensionImpl.KIND, new ExplodedDirectoryModuleExtensionImpl());
    }
    return extension;
  }
}
