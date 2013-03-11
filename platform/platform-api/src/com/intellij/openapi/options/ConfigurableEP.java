/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableFactory;
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

  private final AtomicNotNullLazyValue<NullableFactory<T>> myFactory;
  private PicoContainer myPicoContainer;
  private Project myProject;

  @SuppressWarnings("UnusedDeclaration")
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
    myFactory = new AtomicNotNullLazyValue<NullableFactory<T>>() {
      @NotNull
      @Override
      protected NullableFactory<T> compute() {
        if (providerClass != null) {
          return new InstanceFromProviderFactory();
        }
        else if (instanceClass != null) {
          return new NewInstanceFactory();
        }
        else if (implementationClass != null) {
          return new ImplementationFactory();
        }
        throw new RuntimeException();
      }
    };
  }

  @Nullable
  public T createConfigurable() {
    try {
      return myFactory.getValue().create();
    }
    catch (LinkageError e) {
      LOG.error(e);
    }
    catch (Exception e) {
      LOG.error(e);
    }
    catch (AssertionError e) {
      LOG.error(e);
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

  private class InstanceFromProviderFactory extends AtomicNotNullLazyValue<ConfigurableProvider> implements NullableFactory<T> {
    public T create() {
      return (T)getValue().createConfigurable();
    }

    @NotNull
    @Override
    protected ConfigurableProvider compute() {
      try {
        return instantiate(providerClass, myPicoContainer);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private class NewInstanceFactory extends NotNullLazyValue<Class<? extends T>> implements NullableFactory<T> {
    public T create() {
      return instantiate(getValue(), myPicoContainer, true);
    }

    @NotNull
    @Override
    protected Class<? extends T> compute() {
      try {
        return findClass(instanceClass);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private class ImplementationFactory extends AtomicNotNullLazyValue<T> implements NullableFactory<T> {
    @Override
    public T create() {
      return compute();
    }

    @NotNull
    @Override
    protected T compute() {
      try {
        final Class<T> aClass = findClass(implementationClass);
        return instantiate(aClass, myPicoContainer, true);
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
