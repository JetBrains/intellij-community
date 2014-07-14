/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.util.ReflectionUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

/**
 * Utility class for adding a specific listener to all components in a Swing component tree,
 * with the possibility to exclude components of specific types.
 *
 * @since 5.1
 */
public abstract class ComponentTreeWatcher {
  protected final Class[] myControlsToIgnore;

  protected ComponentTreeWatcher(final Class[] controlsToIgnore) {
    myControlsToIgnore = controlsToIgnore;
  }

  private final ContainerListener myContainerListener = new ContainerListener() {
    public void componentAdded(ContainerEvent e) {
      register(e.getChild());
    }

    public void componentRemoved(ContainerEvent e) {
      unregister(e.getChild());
    }
  };

  private boolean shouldBeIgnored(Object object) {
    if (object instanceof CellRendererPane) return true;
    if (object == null) {
      return true;
    }
    for (Class aClass : myControlsToIgnore) {
      if (ReflectionUtil.isAssignable(aClass, object.getClass())) {
        return true;
      }
    }
    return false;
  }

  public final void register(Component parentComponent) {
    if (shouldBeIgnored(parentComponent)) {
      return;
    }

    if (parentComponent instanceof Container && processChildren((Container)parentComponent)) {
      Container container = (Container)parentComponent;
      for (int i = 0; i < container.getComponentCount(); i++) {
        register(container.getComponent(i));
      }
      container.addContainerListener(myContainerListener);
    }

    processComponent(parentComponent);
  }

  protected boolean processChildren(Container container) {
    return true;
  }

  protected abstract void processComponent(Component parentComponent);

  private void unregister(Component component) {

    if (component instanceof Container && processChildren((Container)component)) {
      Container container = (Container)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        unregister(container.getComponent(i));
      }
      container.removeContainerListener(myContainerListener);
    }

    unprocessComponent(component);
  }

  protected abstract void unprocessComponent(Component component);
}
