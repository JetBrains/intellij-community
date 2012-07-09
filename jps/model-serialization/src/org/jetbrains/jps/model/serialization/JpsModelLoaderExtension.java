package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementProperties;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsSdkType;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleType;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class JpsModelLoaderExtension {

  public void loadRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
  }

  @Nullable
  public JpsOrderRootType getRootType(@NotNull String typeId) {
    return null;
  }

  @Nullable
  public JpsSdkType<?> getSdkType(@NotNull String typeId) {
    return null;
  }

  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
  }

  @Nullable
  public JpsElementReference<? extends JpsCompositeElement> createLibraryTableReference(String tableLevel) {
    return null;
  }

  @Nullable
  public JpsModuleType<?> getModuleType(@NotNull String typeId) {
    return null;
  }

  @Nullable
  public <P extends JpsElementProperties> P loadModuleProperties(JpsModuleType<P> type, Element moduleRoot) {
    return null;
  }

  @Nullable
  public JpsLibraryType<?> getLibraryType(@NotNull String typeId) {
    return null;
  }

  @Nullable
  public <P extends JpsElementProperties> P loadLibraryProperties(JpsLibraryType<P> type, Element propertiesElement) {
    return null;
  }

  @NotNull
  public List<JpsSdkPropertiesLoader<?>> getSdkPropertiesLoaders() {
    return Collections.emptyList();
  }

  @Nullable
  public JpsOrderRootType getSdkRootType(@NotNull String typeId) {
    return null;
  }
}
