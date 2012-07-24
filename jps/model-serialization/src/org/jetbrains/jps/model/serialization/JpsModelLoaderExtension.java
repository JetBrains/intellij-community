package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementLoader;
import org.jetbrains.jps.model.serialization.facet.JpsModuleExtensionLoader;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class JpsModelLoaderExtension {

  public void loadRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
  }

  public void loadProjectRoots(JpsProject project, Element rootManagerElement) {
  }

  @Nullable
  public JpsOrderRootType getRootType(@NotNull String typeId) {
    return null;
  }

  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
  }

  @Nullable
  public JpsElementReference<? extends JpsCompositeElement> createLibraryTableReference(String tableLevel) {
    return null;
  }

  @NotNull
  public List<? extends JpsModulePropertiesLoader<?>> getModulePropertiesLoaders() {
    return Collections.emptyList();
  }

  @NotNull
  public List<JpsLibraryPropertiesLoader<?>> getLibraryPropertiesLoaders() {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends JpsSdkPropertiesLoader<?>> getSdkPropertiesLoaders() {
    return Collections.emptyList();
  }

  public List<? extends JpsModuleExtensionLoader<?>> getModuleExtensionLoaders() {
    return Collections.emptyList();
  }

  public List<? extends JpsPackagingElementLoader<?>> getPackagingElementLoaders() {
    return Collections.emptyList();
  }

  @Nullable
  public JpsOrderRootType getSdkRootType(@NotNull String typeId) {
    return null;
  }

  @Nullable
  public JpsArtifactType getArtifactType(@NotNull String typeId) {
    return null;
  }
}
