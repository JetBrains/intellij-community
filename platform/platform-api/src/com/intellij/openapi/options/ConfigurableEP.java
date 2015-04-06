/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.util.ResourceBundle;

/**
 * @author nik
 * @see Configurable
 */
@Tag("configurable")
public class ConfigurableEP<T extends UnnamedConfigurable> extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.ConfigurableEP");

  @Attribute("displayName")
  public String displayName;

  @Attribute("key")
  public String key;

  @Attribute("bundle")
  public String bundle;

  public String getDisplayName() {
    if (displayName != null) return displayName;
    LOG.assertTrue(bundle != null, "Bundle missed for " + instanceClass);
    final ResourceBundle resourceBundle = AbstractBundle.getResourceBundle(bundle, myPluginDescriptor.getPluginClassLoader());
    return displayName = CommonBundle.message(resourceBundle, key);
  }

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public ConfigurableEP[] children;

  /**
   * Extension point of ConfigurableEP type to calculate children
   */
  @Attribute("childrenEPName")
  public String childrenEPName;

  /**
   * Indicates that configurable has dynamically calculated children.
   * {@link com.intellij.openapi.options.Configurable.Composite#getConfigurables()} will be called for such configurables.
   */
  @Attribute("dynamic")
  public boolean dynamic;

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

  @Attribute("id")
  public String id;

  @Attribute("groupId")
  public String groupId;

  @Attribute("groupWeight")
  public int groupWeight;

  /** Marks project level configurables that do not apply to the default project. */
  @Attribute("nonDefaultProject")
  public boolean nonDefaultProject;

  public boolean isAvailable() {
    return !nonDefaultProject || !(myProject != null  && myProject.isDefault());
  }

  /**
   * @deprecated use '{@link #instanceClass instance}' or '{@link #providerClass provider}' attribute instead
   */
  @Attribute("implementation")
  public String implementationClass;
  @Attribute("instance")
  public String instanceClass;
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
    myProducer = new AtomicNotNullLazyValue<ObjectProducer>() {
      @NotNull
      @Override
      protected ObjectProducer compute() {
        try {
          if (providerClass != null) {
            return new ProviderProducer((ConfigurableProvider)instantiate(providerClass, myPicoContainer));
          }
          if (instanceClass != null) {
            return new ClassProducer(myPicoContainer, findClass(instanceClass));
          }
          if (implementationClass != null) {
            return new ClassProducer(myPicoContainer, findClass(implementationClass));
          }
          throw new RuntimeException("configurable class name is not set");
        }
        catch (AssertionError error) {
          LOG.error(error);
        }
        catch (LinkageError error) {
          LOG.error(error);
        }
        catch (Exception exception) {
          LOG.error(exception);
        }
        return new ObjectProducer();
      }
    };
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

  private static class ObjectProducer {
    Object createElement() {
      return null;
    }

    boolean canCreateElement() {
      return false;
    }

    Class<?> getType() {
      return null;
    }
  }

  private static final class ProviderProducer extends ObjectProducer {
    private final ConfigurableProvider myProvider;

    private ProviderProducer(ConfigurableProvider provider) {
      myProvider = provider;
    }

    @Override
    Object createElement() {
      return myProvider == null ? null : myProvider.createConfigurable();
    }

    @Override
    boolean canCreateElement() {
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
    Object createElement() {
      try {
        return instantiate(myType, myContainer, true);
      }
      catch (AssertionError error) {
        LOG.error(error);
      }
      catch (LinkageError error) {
        LOG.error(error);
      }
      catch (Exception exception) {
        LOG.error(exception);
      }
      return null;
    }

    @Override
    boolean canCreateElement() {
      return myType != null;
    }

    Class<?> getType() {
      return myType;
    }
  }
}
