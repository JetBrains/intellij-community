/*****************************************************************************
 * Copyright (C) PicoContainer Organization. All rights reserved.            *
 * ------------------------------------------------------------------------- *
 * The software in this package is published under the terms of the BSD      *
 * style license a copy of which has been included with this distribution in *
 * the LICENSE.txt file.                                                     *
 *                                                                           *
 * Original code by                                                          *
 *****************************************************************************/
package org.picocontainer.defaults;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.Parameter;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/**
 * A BasicComponentParameter should be used to pass in a particular component as argument to a
 * different component's constructor. This is particularly useful in cases where several
 * components of the same type have been registered, but with a different key. Passing a
 * ComponentParameter as a parameter when registering a component will give PicoContainer a hint
 * about what other component to use in the constructor. This Parameter will never resolve
 * against a collecting type, that is not directly registered in the PicoContainer itself.
 *
 * @author Jon Tirs&eacute;n
 * @author Aslak Helles&oslash;y
 * @author J&ouml;rg Schaible
 * @author Thomas Heller
 * @version $Revision: 2817 $
 */
public class BasicComponentParameter
  implements Parameter, Serializable {

  private final Object componentKey;

  /**
   * Expect a parameter matching a component of a specific key.
   *
   * @param componentKey the key of the desired component
   */
  public BasicComponentParameter(Object componentKey) {
    this.componentKey = componentKey;
  }

  /**
   * Check wether the given Parameter can be statisfied by the container.
   *
   * @return <code>true</code> if the Parameter can be verified.
   * @throws PicoInitializationException {@inheritDoc}
   * @see Parameter#isResolvable(PicoContainer,
   * ComponentAdapter, Class)
   */
  @Override
  public boolean isResolvable(PicoContainer container, ComponentAdapter adapter, Class expectedType) {
    return resolveAdapter(container, adapter, expectedType) != null;
  }

  @Override
  public Object resolveInstance(PicoContainer container, ComponentAdapter adapter, Class expectedType) {
    final ComponentAdapter componentAdapter = resolveAdapter(container, adapter, expectedType);
    if (componentAdapter != null) {
      return container.getComponentInstance(componentAdapter.getComponentKey());
    }
    return null;
  }

  @Override
  public void verify(PicoContainer container, ComponentAdapter adapter, Class expectedType) {
    final ComponentAdapter componentAdapter = resolveAdapter(container, adapter, expectedType);
    if (componentAdapter == null) {
      throw new UnsatisfiableDependenciesException(adapter, Collections.singleton(expectedType), container);
    }
  }

  private ComponentAdapter resolveAdapter(PicoContainer container, ComponentAdapter adapter, Class expectedType) {

    final ComponentAdapter result = getTargetAdapter(container, expectedType, adapter);
    if (result == null) {
      return null;
    }

    if (!expectedType.isAssignableFrom(result.getComponentImplementation())) {
      // check for primitive value
      if (expectedType.isPrimitive()) {
        try {
          final Field field = result.getComponentImplementation().getField("TYPE");
          final Class type = (Class)field.get(result.getComponentInstance(null));
          if (expectedType.isAssignableFrom(type)) {
            return result;
          }
        }
        catch (NoSuchFieldException ignored) {
        }
        catch (IllegalArgumentException ignored) {
        }
        catch (IllegalAccessException ignored) {
        }
        catch (ClassCastException ignored) {
        }
      }
      return null;
    }
    return result;
  }

  private ComponentAdapter getTargetAdapter(PicoContainer container, Class expectedType, ComponentAdapter excludeAdapter) {
    if (componentKey != null) {
      // key tells us where to look so we follow
      return container.getComponentAdapter(componentKey);
    }
    else if (excludeAdapter == null) {
      return container.getComponentAdapterOfType(expectedType);
    }
    else {
      Object excludeKey = excludeAdapter.getComponentKey();
      ComponentAdapter byKey = container.getComponentAdapter(expectedType);
      if (byKey != null && !excludeKey.equals(byKey.getComponentKey())) {
        return byKey;
      }
      List found = container.getComponentAdaptersOfType(expectedType);
      ComponentAdapter exclude = null;
      for (Object o : found) {
        ComponentAdapter work = (ComponentAdapter)o;
        if (work.getComponentKey().equals(excludeKey)) {
          exclude = work;
        }
      }
      found.remove(exclude);
      if (found.size() == 0) {
        if (container.getParent() != null) {
          return container.getParent().getComponentAdapterOfType(expectedType);
        }
        else {
          return null;
        }
      }
      else if (found.size() == 1) {
        return (ComponentAdapter)found.get(0);
      }
      else {
        Class[] foundClasses = new Class[found.size()];
        for (int i = 0; i < foundClasses.length; i++) {
          foundClasses[i] = ((ComponentAdapter)found.get(i)).getComponentImplementation();
        }
        throw new AmbiguousComponentResolutionException(expectedType, foundClasses);
      }
    }
  }
}
