/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.AreaPicoContainer;
import org.picocontainer.*;
import org.picocontainer.alternatives.AbstractDelegatingMutablePicoContainer;
import org.picocontainer.defaults.*;

import java.util.*;

/**
 * @author Alexander Kireyev
 */
public class AreaPicoContainerImpl extends AbstractDelegatingMutablePicoContainer implements AreaPicoContainer {
  private ExtensionsAreaImpl myArea;
  private ComponentAdapterFactory myComponentAdapterFactory;

  public AreaPicoContainerImpl(PicoContainer parentPicoContainer, ExtensionsAreaImpl area) {
    this(new MyPicoContainer(parentPicoContainer), area);
  }

  private AreaPicoContainerImpl(MyPicoContainer picoContainer, ExtensionsAreaImpl area) {
    super(picoContainer);
    picoContainer.setWrapperContainer(this);
    myArea = area;
  }

  public ComponentAdapter getComponentAdapter(final Object componentKey) {
    final ComponentAdapter[] result = new ComponentAdapter[] { null };
    accept(new EmptyPicoVisitor() {
      public void visitComponentAdapter(ComponentAdapter componentAdapter) {
        if (componentKey.equals(componentAdapter.getComponentKey())) {
          result[0] = componentAdapter;
        }
      }
    });
    if (result[0] != null) {
      return result[0];
    }
    else {
      if (getParent() != null) {
        return getParent().getComponentAdapter(componentKey);
      }
      else {
        return null;
      }
    }
  }

  public List getComponentInstances() {
    final List result = new ArrayList();
    if (getParent() != null) {
      List parentInstances = getParent().getComponentInstances();
      result.addAll(parentInstances);
    }
    accept(new EmptyPicoVisitor() {
      public void visitContainer(PicoContainer pico) {
        result.addAll(pico.getComponentInstances());
      }
    });
    return result;
  }

  public List getComponentInstancesOfType(final Class type) {
    final List result = new ArrayList();
    if (getParent() != null) {
      List parentInstances = getParent().getComponentInstancesOfType(type);
      result.addAll(parentInstances);
    }
    accept(new EmptyPicoVisitor() {
      public void visitContainer(PicoContainer pico) {
        result.addAll(pico.getComponentInstancesOfType(type));
      }
    });
    return result;
  }

  public Object getComponentInstanceOfType(Class componentType) {
    List instances = getComponentInstancesOfType(componentType);
    if (instances.size() == 0) {
      return null;
    }
    else if (instances.size() == 1) {
      return instances.get(0);
    }
    else {
      throw new AmbiguousComponentResolutionException(componentType, instances.toArray(new Object[instances.size()]));
    }
  }

  public Object getComponentInstance(final Object componentKey) {
    final Object[] result = new Object[] { null };
    accept(new EmptyPicoVisitor() {
      public void visitContainer(PicoContainer pico) {
        final boolean[] found = new boolean[] { false };
        pico.accept(new EmptyPicoVisitor() {
          public void visitComponentAdapter(ComponentAdapter componentAdapter) {
            if (componentKey.equals(componentAdapter.getComponentKey())) {
              found[0] = true;
            }
          }
        });
        if (!found[0]) {
          return;
        }
        Object componentInstance = pico.getComponentInstance(componentKey);
        if (componentInstance != null) {
          result[0] = componentInstance;
        }
      }
    });
    if (result[0] != null) {
      return result[0];
    }
    if (getParent() != null) {
      Object parentInstance = getParent().getComponentInstance(componentKey);
      if (parentInstance != null) {
        return parentInstance;
      }
    }
    return null;
  }

  public ComponentAdapter getComponentAdapterOfType(Class componentType) {
    List adapters = getComponentAdaptersOfType(componentType);
    if (adapters.size() == 0) {
      return null;
    }
    else if (adapters.size() == 1) {
      return (ComponentAdapter) adapters.get(0);
    }
    else {
      Class[] foundClasses = new Class[adapters.size()];
      for (int i = 0; i < foundClasses.length; i++) {
          ComponentAdapter componentAdapter = (ComponentAdapter) adapters.get(i);
          foundClasses[i] = componentAdapter.getComponentImplementation();
      }
      throw new AmbiguousComponentResolutionException(componentType, foundClasses);
    }
  }

  public Collection getComponentAdapters() {
    final Set result = new HashSet();
    if (getParent() != null) {
      result.addAll(getParent().getComponentAdapters());
    }

    result.addAll(super.getComponentAdapters());

    accept(new EmptyPicoVisitor() {
      public void visitContainer(PicoContainer pico) {
        if (pico != getDelegate()) result.addAll(pico.getComponentAdapters());
      }
    });

    return result;
  }

  public List getComponentAdaptersOfType(final Class componentType) {
    final Set result = new HashSet();
    if (getParent() != null) {
      result.addAll(getParent().getComponentAdaptersOfType(componentType));
    }

    final List componentAdapters = new ArrayList(getComponentAdapters());
    for (Iterator iterator = componentAdapters.iterator(); iterator.hasNext();) {
      ComponentAdapter componentAdapter = (ComponentAdapter) iterator.next();
      if (componentType.isAssignableFrom(componentAdapter.getComponentImplementation())) {
        result.add(componentAdapter);
      }
    }

    return new ArrayList(result);
  }

  public MutablePicoContainer makeChildContainer() {
    throw new UnsupportedOperationException("Method makeChildContainer() is not implemented");
  }

  public void setComponentAdapterFactory(ComponentAdapterFactory factory) {
    myComponentAdapterFactory = factory;
  }

  public ComponentAdapterFactory getComponentAdapterFactory() {
    return myComponentAdapterFactory;
  }

  private abstract class EmptyPicoVisitor extends AbstractPicoVisitor {
    public void visitContainer(PicoContainer pico) {
    }

    public void visitComponentAdapter(ComponentAdapter componentAdapter) {
    }

    public void visitParameter(Parameter parameter) {
    }
  }

  private static class MyPicoContainer extends DefaultPicoContainer {
    private AreaPicoContainerImpl myWrapperContainer;
    private ComponentAdapterFactory myDefault = new DefaultComponentAdapterFactory();

    public MyPicoContainer(final PicoContainer parentPicoContainer) {
      super(parentPicoContainer);
    }

    public void setWrapperContainer(AreaPicoContainerImpl wrapperContainer) {
      myWrapperContainer = wrapperContainer;
    }

    public ComponentAdapter registerComponentImplementation(Object componentKey, Class componentImplementation, Parameter[] parameters) throws PicoRegistrationException {
        ComponentAdapter componentAdapter = getAdapterFactory().createComponentAdapter(componentKey, componentImplementation, parameters);
        registerComponent(componentAdapter);
        return componentAdapter;
    }

    private ComponentAdapterFactory getAdapterFactory() {
      return myWrapperContainer.getComponentAdapterFactory() != null ? myWrapperContainer.getComponentAdapterFactory() : myDefault;
    }
  }
}
