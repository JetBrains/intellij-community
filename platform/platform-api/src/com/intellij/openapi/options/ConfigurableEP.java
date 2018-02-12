/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.options;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.util.ResourceBundle;

/**
 * Declares a named component that enables to configure settings.
 *
 * @author nik
 * @see Configurable
 */
@Tag("configurable")
public class ConfigurableEP<T extends UnnamedConfigurable> extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.ConfigurableEP");

  /**
   * This attribute specifies the setting name visible to users.
   * It has precedence over the pair of attributes {@link #key}-{@link #bundle}.
   * If the display name is not set, a configurable component will be instantiated to retrieve its name dynamically.
   * This causes a loading of plugin classes and increases the delay before showing the settings dialog.
   * It is highly recommended specifying the display name in XML to improve UI responsiveness.
   */
  @Attribute("displayName")
  public String displayName;

  /**
   * This attribute specifies the resource key in the specified {@link #bundle}.
   * This is another way to specify the {@link #displayName display name}.
   */
  @Attribute("key")
  public String key;

  /**
   * This attribute specifies the resource bundle that contains the specified {@link #key}.
   * This is another way to specify the {@link #displayName display name}.
   */
  @Attribute("bundle")
  public String bundle;

  public String getDisplayName() {
    if (displayName != null) return displayName;
    LOG.assertTrue(bundle != null, "Bundle missed for " + instanceClass);
    final ResourceBundle resourceBundle = AbstractBundle.getResourceBundle(bundle, myPluginDescriptor.getPluginClassLoader());
    return displayName = CommonBundle.message(resourceBundle, key);
  }

  /**
   * @return a resource bundle using the specified base name or {@code null}
   */
  public ResourceBundle findBundle() {
    return bundle == null ? null : AbstractBundle.getResourceBundle(bundle, myPluginDescriptor != null
                                                                            ? myPluginDescriptor.getPluginClassLoader()
                                                                            : getClass().getClassLoader());
  }

  @Property(surroundWithTag = false)
  @XCollection
  public ConfigurableEP[] children;

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

  public ConfigurableEP[] getChildren() {
    for (ConfigurableEP child : children) {
      child.myPicoContainer = myPicoContainer;
      child.myPluginDescriptor = myPluginDescriptor;
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
    return !nonDefaultProject || !(myProject != null  && myProject.isDefault());
  }

  /**
   * @deprecated use '{@link #instanceClass instance}' or '{@link #providerClass provider}' attribute instead
   */
  @Deprecated
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

  private final AtomicNotNullLazyValue<ObjectProducer> myProducer;
  private PicoContainer myPicoContainer;
  private Project myProject;

  public ConfigurableEP() {
    this(ApplicationManager.getApplication().getPicoContainer(), null);
  }

  @SuppressWarnings("UnusedDeclaration")
  public ConfigurableEP(Project project) {
    this(project.getPicoContainer(), project);
  }

  protected ConfigurableEP(PicoContainer picoContainer, @Nullable Project project) {
    myProject = project;
    myPicoContainer = picoContainer;
    myProducer = AtomicNotNullLazyValue.createValue(this::createProducer);
  }

  @NotNull
  protected ObjectProducer createProducer() {
    try {
      if (providerClass != null) {
        return new ProviderProducer(instantiate(providerClass, myPicoContainer));
      }
      if (instanceClass != null) {
        return new ClassProducer(myPicoContainer, findClass(instanceClass));
      }
      if (implementationClass != null) {
        return new ClassProducer(myPicoContainer, findClass(implementationClass));
      }
      throw new RuntimeException("configurable class name is not set");
    }
    catch (AssertionError | Exception | LinkageError error) {
      LOG.error(error);
    }
    return new ObjectProducer();
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
   * @return the the configurable's type or {@code null}
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
    private final ConfigurableProvider myProvider;

    private ProviderProducer(ConfigurableProvider provider) {
      myProvider = provider;
    }

    @Override
    protected Object createElement() {
      return myProvider == null ? null : myProvider.createConfigurable();
    }

    @Override
    protected boolean canCreateElement() {
      return myProvider != null && myProvider.canCreateConfigurable();
    }
  }

  private static final class ClassProducer extends ObjectProducer {
    private final PicoContainer myContainer;
    private final Class<?> myType;

    private ClassProducer(PicoContainer container, Class<?> type) {
      myContainer = container;
      myType = type;
    }

    @Override
    protected Object createElement() {
      try {
        return instantiate(myType, myContainer, true);
      }
      catch (ProcessCanceledException exception) {
        throw exception;
      }
      catch (AssertionError | LinkageError | Exception e) {
        LOG.error(e);
      }
      return null;
    }

    @Override
    protected boolean canCreateElement() {
      return myType != null;
    }

    protected Class<?> getType() {
      return myType;
    }
  }
}