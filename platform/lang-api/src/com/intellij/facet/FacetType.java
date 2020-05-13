// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet;

import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.facet.ui.DefaultFacetSettingsEditor;
import com.intellij.facet.ui.FacetEditor;
import com.intellij.facet.ui.MultipleFacetSettingsEditor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Override this class to provide custom type of facets. The implementation should be registered in your {@code plugin.xml}:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;facetType implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 */
public abstract class FacetType<F extends Facet, C extends FacetConfiguration> implements PluginAware {
  public static final ExtensionPointName<FacetType> EP_NAME = ExtensionPointName.create("com.intellij.facetType");

  private final @NotNull FacetTypeId<F> myId;
  private final @NotNull String myStringId;
  private final @NotNull String myPresentableName;
  private final @Nullable FacetTypeId myUnderlyingFacetType;
  private PluginDescriptor myPluginDescriptor;

  public static <T extends FacetType> T findInstance(Class<T> aClass) {
    return EP_NAME.findExtension(aClass);
  }

  /**
   * @param id                  unique instance of {@link FacetTypeId}
   * @param stringId            unique string id of the facet type
   * @param presentableName     name of this facet type which will be shown in UI
   * @param underlyingFacetType if this parameter is not {@code null} then you will be able to add facets of this type only as
   *                            subfacets to a facet of the specified type. If this parameter is {@code null} it will be possible to add facet of this type
   *                            directly to a module
   */
  public FacetType(final @NotNull FacetTypeId<F> id,
                   final @NotNull @NonNls String stringId,
                   final @NotNull @Nls(capitalization = Nls.Capitalization.Title) String presentableName,
                   final @Nullable FacetTypeId underlyingFacetType) {
    myId = id;
    myStringId = stringId;
    myPresentableName = presentableName;
    myUnderlyingFacetType = underlyingFacetType;
  }

  /**
   * @param id              unique instance of {@link FacetTypeId}
   * @param stringId        unique string id of the facet type
   * @param presentableName name of this facet type which will be shown in UI
   */
  public FacetType(final @NotNull FacetTypeId<F> id,
                   final @NotNull @NonNls String stringId,
                   final @NotNull @Nls(capitalization = Nls.Capitalization.Title) String presentableName) {
    this(id, stringId, presentableName, null);
  }

  @NotNull
  public final FacetTypeId<F> getId() {
    return myId;
  }

  @NotNull
  public final String getStringId() {
    return myStringId;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  /**
   * Default name which will be used then user creates a facet of this type.
   */
  @NotNull
  @NonNls
  public String getDefaultFacetName() {
    return myPresentableName;
  }

  @Nullable
  public FacetTypeId<?> getUnderlyingFacetType() {
    return myUnderlyingFacetType;
  }

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public final PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  /**
   * @deprecated this method is not called by IDE core anymore. Use {@link com.intellij.framework.detection.FrameworkDetector} extension
   * to provide automatic detection for facets
   */
  @SuppressWarnings("unused")
  @Deprecated
  public void registerDetectors(FacetDetectorRegistry<C> registry) {
  }

  /**
   * Create default configuration of facet. See {@link FacetConfiguration} for details.
   */
  public abstract C createDefaultConfiguration();

  /**
   * Create a new facet instance.
   *
   * @param module          parent module for facet. Must be passed to {@link Facet} constructor
   * @param name            name of facet. Must be passed to {@link Facet} constructor
   * @param configuration   facet configuration. Must be passed to {@link Facet} constructor
   * @param underlyingFacet underlying facet. Must be passed to {@link Facet} constructor
   * @return a created facet
   */
  public abstract F createFacet(@NotNull Module module, final String name, @NotNull C configuration, @Nullable Facet underlyingFacet);

  /**
   * @return {@code true} if only one facet of this type is allowed within the containing module (if this type doesn't have the underlying
   * facet type) or within the underlying facet
   */
  public boolean isOnlyOneFacetAllowed() {
    return true;
  }

  /**
   * @param moduleType type of module
   * @return {@code true} if facet of this type are allowed in module of type {@code moduleType}
   */
  public abstract boolean isSuitableModuleType(ModuleType moduleType);

  @Nullable
  public Icon getIcon() {
    return null;
  }

  /**
   * Returns the topic in the help file which is shown when help for this facet type is requested.
   *
   * @return the help topic, or null if no help is available.
   */
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  public DefaultFacetSettingsEditor createDefaultConfigurationEditor(@NotNull Project project, @NotNull C configuration) {
    return null;
  }

  /**
   * Override to allow editing several facets at once.
   *
   * @param project project
   * @param editors editors of selected facets
   * @return editor
   */
  @Nullable
  public MultipleFacetSettingsEditor createMultipleConfigurationsEditor(@NotNull Project project, FacetEditor @NotNull [] editors) {
    return null;
  }
}
