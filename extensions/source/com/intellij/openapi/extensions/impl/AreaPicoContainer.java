/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.Parameter;
import org.picocontainer.defaults.DefaultPicoContainer;
import org.picocontainer.defaults.AmbiguousComponentResolutionException;
import org.picocontainer.defaults.AbstractPicoVisitor;
import org.picocontainer.alternatives.AbstractDelegatingMutablePicoContainer;

import java.util.*;

/**
 * @author Alexander Kireyev
 */
public class AreaPicoContainer extends AbstractDelegatingMutablePicoContainer implements MutablePicoContainer {
  private ExtensionsAreaImpl myArea;

  public AreaPicoContainer(PicoContainer parentPicoContainer, ExtensionsAreaImpl area) {
    super(new DefaultPicoContainer(parentPicoContainer));
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
    accept(new EmptyPicoVisitor() {
      public void visitContainer(PicoContainer pico) {
        result.addAll(pico.getComponentInstances());
      }
    });
    return result;
  }

  public List getComponentInstancesOfType(final Class type) {
    final List result = new ArrayList();
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
    if (getParent() != null) {
      Object parentInstance = getParent().getComponentInstance(componentKey);
      if (parentInstance != null) {
        return parentInstance;
      }
    }

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
    return result[0];
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
    final List result = new ArrayList();
    if (getParent() != null) {
      result.addAll(getParent().getComponentAdapters());
    }
    accept(new EmptyPicoVisitor() {
      public void visitComponentAdapter(ComponentAdapter componentAdapter) {
        result.add(componentAdapter);
      }
    });
    return result;
  }

  public List getComponentAdaptersOfType(final Class componentType) {
    final List result = new ArrayList();
    if (getParent() != null) {
      result.addAll(getParent().getComponentAdaptersOfType(componentType));
    }
    accept(new EmptyPicoVisitor() {
      public void visitComponentAdapter(ComponentAdapter componentAdapter) {
        if (componentType.isAssignableFrom(componentAdapter.getComponentImplementation())) {
          result.add(componentAdapter);
        }
      }
    });
    return result;
  }

  public MutablePicoContainer makeChildContainer() {
    throw new UnsupportedOperationException("Method makeChildContainer() is not implemented");
  }

  private abstract class EmptyPicoVisitor extends AbstractPicoVisitor {
    public void visitContainer(PicoContainer pico) {
    }

    public void visitComponentAdapter(ComponentAdapter componentAdapter) {
    }

    public void visitParameter(Parameter parameter) {
    }
  }
}
