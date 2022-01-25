// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.BundleBase;
import com.intellij.DynamicBundle;
import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ResourceBundle;

/**
 * Declares a named component that enables to configure settings.
 *
 * @see Configurable
 */
@Tag("configurable")
public class ConfigurableEP<T extends UnnamedConfigurable> implements PluginAware {
  private static final Logger LOG = Logger.getInstance(ConfigurableEP.class);

  private PluginDescriptor pluginDescriptor;

  @Transient
  public final PluginDescriptor getPluginDescriptor() {
    return pluginDescriptor;
  }

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor value) {
    pluginDescriptor = value;
  }

  /**
   * This attribute specifies the setting name visible to users.
   * It has precedence over the pair of attributes {@link #key}-{@link #bundle}.
   * If the display name is not set, a configurable component will be instantiated to retrieve its name dynamically.
   * This causes a loading of plugin classes and increases the delay before showing the settings dialog.
   * It is highly recommended specifying the display name in XML to improve UI responsiveness.
   */
  @Attribute("displayName")
  @Nls(capitalization = Nls.Capitalization.Title)
  public String displayName;

  /**
   * This attribute specifies the resource key in the specified {@link #bundle}.
   * This is another way to specify the {@link #displayName display name}.
   */
  @Attribute("key")
  @Nls(capitalization = Nls.Capitalization.Title)
  public String key;

  /**
   * This attribute specifies the resource bundle that contains the specified {@link #key}.
   * This is another way to specify the {@link #displayName display name}.
   */
  @Attribute("bundle")
  public String bundle;

  @NotNull
  @NlsContexts.ConfigurableName
  public String getDisplayName() {
    if (displayName != null) {
      return displayName;
    }

    ResourceBundle resourceBundle = findBundle();
    if (resourceBundle == null || key == null) {
      if (key == null) {
        LOG.warn("Bundle key missed for " + displayName);
      }
      else {
        LOG.warn("Bundle missed for " + displayName);
      }

      if (providerClass == null) {
        //noinspection HardCodedStringLiteral
        return instanceClass == null ? implementationClass : instanceClass;
      }
      else {
        //noinspection HardCodedStringLiteral
        return providerClass;
      }
    }
    else {
      return BundleBase.messageOrDefault(resourceBundle, key, null);
    }
  }

  /**
   * @return a resource bundle using the specified base name or {@code null}
   */
  @Nullable
  public ResourceBundle findBundle() {
    String pathToBundle = findPathToBundle();
    if (pathToBundle == null) {
      // a path to bundle is not specified or cannot be found
      return null;
    }
    ClassLoader loader = pluginDescriptor == null ? null : pluginDescriptor.getPluginClassLoader();
    return DynamicBundle.INSTANCE.getResourceBundle(pathToBundle, loader != null ? loader : getClass().getClassLoader());
  }

  @Nullable
  private String findPathToBundle() {
    if (bundle == null && pluginDescriptor != null) {
      // can be unspecified
      return pluginDescriptor.getResourceBundleBaseName();
    }
    return bundle;
  }

  @Property(surroundWithTag = false)
  @XCollection
  public List<ConfigurableEP<?>> children;

  /**
   * This attribute specifies a name of the extension point of {@code ConfigurableEP} type that will be used to calculate children.
   *
   * @see #dynamic
   */
  @Attribute("childrenEPName")
  public String childrenEPName;

  /**
   * This attribute states that a custom configurable component implements the {@link Configurable.Composite} interface
   * and its children are dynamically calculated by calling the {@link Configurable.Composite#getConfigurables()} method.
   * It is needed to improve performance, because we do not want to load any additional classes during the building a setting tree.
   */
  @Attribute("dynamic")
  public boolean dynamic;

  /**
   * This attribute is used to create a hierarchy of settings.
   * If it is set, the configurable component will be a child of the specified parent component.
   *
   * @see #groupId
   */
  @Attribute("parentId")
  public String parentId;

  @NotNull
  public List<ConfigurableEP<?>> getChildren() {
    for (ConfigurableEP<?> child : children) {
      child.componentManager = componentManager;
      child.pluginDescriptor = pluginDescriptor;
      child.myProject = myProject;
    }
    return children;
  }

  /**
   * This attribute specifies the {@link SearchableConfigurable#getId() unique identifier} of the configurable component.
   * It is also recommended specifying the identifier in XML to improve UI responsiveness.
   */
  @Attribute("id")
  public String id;

  /**
   * This attribute specifies a top-level group, which the configurable component belongs to.
   * If this attribute is not set, the configurable component will be added to the Other Settings group.
   * The following groups are supported:
   * <dl>
   * <dt>ROOT {@code groupId="root"}</dt>
   * <dd>This is the invisible root group that contains all other groups.
   * Usually, you should not place your settings here.</dd>
   * <dt>Appearance & Behavior {@code groupId="appearance"}</dt>
   * <dd>This group contains settings to personalize IDE appearance and behavior:
   * change themes and font size, tune the keymap, and configure plugins and system settings,
   * such as password policies, HTTP proxy, updates and more.</dd>
   * <dt>Editor {@code groupId="editor"}</dt>
   * <dd>This group contains settings to personalize source code appearance by changing fonts,
   * highlighting styles, indents, etc.  Here you can customize the editor from line numbers,
   * caret placement and tabs to source code inspections, setting up templates and file encodings.</dd>
   * <dt>Default Project / Project Settings {@code groupId="project"}</dt>
   * <dd>This group is intended to store some project-related settings, but now it is rarely used.</dd>
   * <dt>Build, Execution, Deployment {@code groupId="build"}</dt>
   * <dd>This group contains settings to configure you project integration with the different build tools,
   * modify the default compiler settings, manage server access configurations, customize the debugger behavior, etc.</dd>
   * <dt>Build Tools {@code groupId="build.tools"}</dt>
   * <dd>This is subgroup of the group above. Here you can configure your project integration
   * with the different build tools, such as Maven, Gradle, or Gant.</dd>
   * <dt>Languages & Frameworks {@code groupId="language"}</dt>
   * <dd>This group is intended to configure the settings related to specific frameworks and technologies used in your project.</dd>
   * <dt>Tools {@code groupId="tools"}</dt>
   * <dd>This group contains settings to configure integration with third-party applications,
   * specify the SSH Terminal connection settings, manage server certificates and tasks, configure diagrams layout, etc.</dd>
   * <dt>Other Settings {@code groupId="other"}</dt>
   * <dd>This group contains settings that are related to non-bundled custom plugins and are not assigned to any other category.</dd>
   * </dl>
   * This attribute should not be used together with the {@link #parentId} attribute, which has precedence.
   * Currently, it is possible to specify a group identifier in the {@link #parentId} attribute.
   */
  @Attribute("groupId")
  public String groupId;

  /**
   * This attribute specifies the weight of a configurable component within a group or a parent configurable component.
   * The default weight is {@code 0}. If one child in a group or a parent configurable component has non-zero weight,
   * all children will be sorted descending by their weight. And if the weights are equal,
   * the components will be sorted ascending by their display name.
   */
  @Attribute("groupWeight")
  public int groupWeight;

  /**
   * This attribute is applicable to the {@code projectConfigurable} extension only.
   * If it is set to {@code true}, the corresponding project settings will be shown for a real project only,
   * not for the {@link com.intellij.openapi.project.ProjectManager#getDefaultProject() template project},
   * which provides default settings for all the new projects.
   */
  @Attribute("nonDefaultProject")
  public boolean nonDefaultProject;

  public boolean isAvailable() {
    return !nonDefaultProject || !(myProject != null && myProject.isDefault());
  }

  /**
   * @deprecated use '{@link #instanceClass instance}' or '{@link #providerClass provider}' attribute instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Attribute("implementation")
  public String implementationClass;

  /**
   * This attribute specifies a qualified name of a custom implementation of this interface.
   * The constructor will be determined automatically from the tag name:
   * <br>{@code <extensions defaultExtensionNs="com.intellij">}
   * <br>{@code &nbsp;&nbsp;&nbsp;&nbsp;<projectConfigurable instance="fully.qualified.class.name"/>}
   * <br>{@code </extensions>}
   *
   * @see #providerClass provider
   */
  @Attribute("instance")
  public String instanceClass;

  /**
   * This attribute can be used instead of the {@link #instanceClass instance} attribute.
   * It specifies a qualified name of a custom implementation of the {@link ConfigurableProvider} interface,
   * which provides another way to create a configurable component:
   * <br>{@code <extensions defaultExtensionNs="com.intellij">}
   * <br>{@code &nbsp;&nbsp;&nbsp;&nbsp;<projectConfigurable provider="fully.qualified.class.name"/>}
   * <br>{@code </extensions>}
   *
   * @see #instanceClass instance
   */
  @Attribute("provider")
  public String providerClass;

  @Attribute("treeRenderer")
  public String treeRendererClass;

  private final NotNullLazyValue<ObjectProducer> myProducer = NotNullLazyValue.atomicLazy(this::createProducer);
  private ComponentManager componentManager;
  private Project myProject;

  @NonInjectable
  public ConfigurableEP(@NotNull PluginDescriptor pluginDescriptor) {
    this(ApplicationManager.getApplication());

    setPluginDescriptor(pluginDescriptor);
  }

  protected ConfigurableEP() {
    this(ApplicationManager.getApplication());
  }

  protected ConfigurableEP(@NotNull ComponentManager componentManager) {
    myProject = componentManager instanceof Project ? (Project)componentManager : null;
    this.componentManager = componentManager;
  }

  @NonInjectable
  public ConfigurableEP(@NotNull Project project) {
    myProject = project;
    componentManager = project;
  }

  @NotNull
  protected ObjectProducer createProducer() {
    try {
      if (providerClass != null) {
        ConfigurableProvider provider = instantiateConfigurableProvider();
        return provider == null ? new ObjectProducer() : new ProviderProducer(provider);
      }
      else if (instanceClass != null) {
        return new ClassProducer(componentManager, instanceClass, pluginDescriptor);
      }
      else if (implementationClass != null) {
        return new ClassProducer(componentManager, implementationClass, pluginDescriptor);
      }
      else {
        throw new PluginException("configurable class name is not set", pluginDescriptor == null ? null : pluginDescriptor.getPluginId());
      }
    }
    catch (AssertionError | Exception | LinkageError error) {
      LOG.error(new PluginException(error, pluginDescriptor == null ? null : pluginDescriptor.getPluginId()));
    }
    return new ObjectProducer();
  }

  @Nullable
  public final ConfigurableProvider instantiateConfigurableProvider() {
    return providerClass != null
           ? componentManager.instantiateClass(providerClass, pluginDescriptor)
           : null;
  }

  public final @Nullable Class<?> findClassOrNull(@NotNull String className) {
    try {
      ClassLoader classLoader = pluginDescriptor == null ? null : pluginDescriptor.getPluginClassLoader();
      return Class.forName(className, true, classLoader);
    }
    catch (Throwable t) {
      if (pluginDescriptor == null) {
        LOG.error(t);
      }
      else {
        LOG.error(new PluginException(t, pluginDescriptor.getPluginId()));
      }
      return null;
    }
  }

  @Nullable
  public T createConfigurable() {
    ObjectProducer producer = myProducer.getValue();
    if (producer.canCreateElement()) {
      @SuppressWarnings("unchecked")
      T configurable = (T)producer.createElement();
      return configurable;
    }
    return null;
  }

  @Nullable
  public ConfigurableTreeRenderer createTreeRenderer() {
    if (treeRendererClass == null) {
      return null;
    }
    try {
      return componentManager.instantiateClass(treeRendererClass, pluginDescriptor);
    }
    catch (ProcessCanceledException exception) {
      throw exception;
    }
    catch (AssertionError | LinkageError | Exception e) {
      LOG.error(new PluginException(e, pluginDescriptor == null ? null : pluginDescriptor.getPluginId()));
    }
    return null;
  }

  public Project getProject() {
    return myProject;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  public boolean canCreateConfigurable() {
    return myProducer.getValue().canCreateElement();
  }

  /**
   * Returns the type of configurable to create or {@code null},
   * if it cannot be determined.
   *
   * @return the configurable's type or {@code null}
   */
  @Nullable
  public Class<?> getConfigurableType() {
    return myProducer.getValue().getType();
  }

  protected static class ObjectProducer {
    protected Object createElement() {
      return null;
    }

    protected boolean canCreateElement() {
      return false;
    }

    protected Class<?> getType() {
      return null;
    }
  }

  private static final class ProviderProducer extends ObjectProducer {
    @NotNull
    private final ConfigurableProvider myProvider;

    private ProviderProducer(@NotNull ConfigurableProvider provider) {
      myProvider = provider;
    }

    @Override
    protected Object createElement() {
      return myProvider.createConfigurable();
    }

    @Override
    protected boolean canCreateElement() {
      return myProvider.canCreateConfigurable();
    }
  }

  private static final class ClassProducer extends ObjectProducer {
    private final ComponentManager componentManager;
    private final String className;
    private final PluginDescriptor pluginDescriptor;

    private ClassProducer(@NotNull ComponentManager componentManager, @NotNull String className, @Nullable PluginDescriptor pluginDescriptor) {
      this.componentManager = componentManager;
      this.className = className;
      this.pluginDescriptor = pluginDescriptor;
    }

    @Override
    protected Object createElement() {
      try {
        return componentManager.instantiateClass(className, pluginDescriptor);
      }
      catch (ProcessCanceledException exception) {
        throw exception;
      }
      catch (ExtensionNotApplicableException ignore) {
        return null;
      }
      catch (AssertionError | LinkageError | Exception e) {
        LOG.error("Cannot create configurable", e);
      }
      return null;
    }

    @Override
    protected boolean canCreateElement() {
      return true;
    }
  }
}
