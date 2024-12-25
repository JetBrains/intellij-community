// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet;

import com.intellij.facet.ui.DefaultFacetSettingsEditor;
import com.intellij.facet.ui.FacetEditor;
import com.intellij.facet.ui.MultipleFacetSettingsEditor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.*;

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
  public static final ExtensionPointName<FacetType> EP_NAME = new ExtensionPointName<>("com.intellij.facetType");

  private final @NotNull FacetTypeId<F> myId;
  private final @NotNull String myStringId;
  private final @Nls(capitalization = Nls.Capitalization.Title) @NotNull String myPresentableName;
  private final @Nullable FacetTypeId myUnderlyingFacetType;
  private PluginDescriptor myPluginDescriptor;

  public static <T extends FacetType<?, ?>> T findInstance(Class<T> aClass) {
    return EP_NAME.findExtension(aClass);
  }

  /**
   * @param id                  unique instance of {@link FacetTypeId}
   * @param stringId            unique string id of the facet type
   * @param presentableName     name of this facet type which will be shown in UI
   * @param underlyingFacetType if this parameter is not {@code null} then you will be able to add facets of this type only as
   *                            subfacets to a facet of the specified type. If this parameter is {@code null} it will be possible to add facet of this type
   *                            directly to a module
   * @deprecated facet types with underlying facet are deprecated (see <a href="https://youtrack.jetbrains.com/issue/IDEA-309067">IDEA-309067</a>), 
   * use {@link #FacetType(FacetTypeId, String, String)} instead
   */
  @Deprecated
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

  public final @NotNull FacetTypeId<F> getId() {
    return myId;
  }

  public final @NotNull String getStringId() {
    return myStringId;
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getPresentableName() {
    return myPresentableName;
  }

  /**
   * Default name which will be used then user creates a facet of this type.
   */
  public @NotNull @NlsSafe String getDefaultFacetName() {
    return myPresentableName;
  }

  @ApiStatus.NonExtendable
  public @Nullable FacetTypeId<?> getUnderlyingFacetType() {
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
  public abstract F createFacet(@NotNull Module module,
                                final @NlsSafe String name,
                                @NotNull C configuration,
                                @Nullable Facet underlyingFacet);

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

  public @Nullable Icon getIcon() {
    return null;
  }

  /**
   * Returns the topic in the help file which is shown when help for this facet type is requested.
   *
   * @return the help topic, or null if no help is available.
   */
  public @Nullable @NonNls String getHelpTopic() {
    return null;
  }

  public @Nullable DefaultFacetSettingsEditor createDefaultConfigurationEditor(@NotNull Project project, @NotNull C configuration) {
    return null;
  }

  /**
   * Override to allow editing several facets at once.
   *
   * @param project project
   * @param editors editors of selected facets
   * @return editor
   */
  public @Nullable MultipleFacetSettingsEditor createMultipleConfigurationsEditor(@NotNull Project project, FacetEditor @NotNull [] editors) {
    return null;
  }
}
