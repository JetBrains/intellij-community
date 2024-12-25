// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactExtensionSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;
import org.jetbrains.jps.model.serialization.library.JpsLibraryPropertiesSerializer;
import org.jetbrains.jps.model.serialization.library.JpsLibraryRootTypeSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleClasspathSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer;
import org.jetbrains.jps.model.serialization.runConfigurations.JpsRunConfigurationPropertiesSerializer;
import org.jetbrains.jps.service.JpsServiceManager;

import java.util.Collections;
import java.util.List;

/**
 * Override this class and register the implementation in META-INF/services/org.jetbrains.jps.model.serialization.JpsModelSerializerExtension
 * to support loading custom entities in the project configuration files (*.iml and .idea).
 */
public abstract class JpsModelSerializerExtension {
  public static Iterable<JpsModelSerializerExtension> getExtensions() {
    return JpsServiceManager.getInstance().getExtensions(JpsModelSerializerExtension.class);
  }

  @ApiStatus.Internal
  public void loadRootModel(@NotNull JpsModule module, @NotNull Element rootModel) {
  }

  public void loadModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
  }

  @ApiStatus.Internal
  public List<JpsLibraryRootTypeSerializer> getLibraryRootTypeSerializers() {
    return Collections.emptyList();
  }

  @ApiStatus.Internal
  public @NotNull List<JpsLibraryRootTypeSerializer> getSdkRootTypeSerializers() {
    return Collections.emptyList();
  }

  @ApiStatus.Internal
  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
  }

  @ApiStatus.Internal
  public @Nullable JpsElementReference<? extends JpsCompositeElement> createLibraryTableReference(String tableLevel) {
    return null;
  }

  @ApiStatus.Internal
  public @Nullable String getLibraryTableLevelId(JpsElementReference<? extends JpsCompositeElement> reference) {
    return null;
  }

  public @NotNull List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.emptyList();
  }

  public @NotNull List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Collections.emptyList();
  }

  public @NotNull List<? extends JpsModulePropertiesSerializer<?>> getModulePropertiesSerializers() {
    return Collections.emptyList();
  }

  public @NotNull List<? extends JpsModuleSourceRootPropertiesSerializer<?>> getModuleSourceRootPropertiesSerializers() {
    return Collections.emptyList();
  }

  public @NotNull List<? extends JpsLibraryPropertiesSerializer<?>> getLibraryPropertiesSerializers() {
    return Collections.emptyList();
  }

  public @NotNull List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesSerializers() {
    return Collections.emptyList();
  }

  public @NotNull List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return Collections.emptyList();
  }

  public @NotNull List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
    return Collections.emptyList();
  }

  public @NotNull List<? extends JpsArtifactPropertiesSerializer<?>> getArtifactTypePropertiesSerializers() {
    return Collections.emptyList();
  }

  @ApiStatus.Internal
  public @NotNull List<? extends JpsArtifactExtensionSerializer<?>> getArtifactExtensionSerializers() {
    return Collections.emptyList();
  }

  @ApiStatus.Internal
  public @Nullable JpsModuleClasspathSerializer getClasspathSerializer() {
    return null;
  }

  public @NotNull List<? extends JpsRunConfigurationPropertiesSerializer<?>> getRunConfigurationPropertiesSerializers() {
    return Collections.emptyList();
  }
}
