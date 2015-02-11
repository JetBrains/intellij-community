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
package com.intellij.openapi;

import javax.swing.CellRendererPane;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

/**
 * @author Sergey.Malenkov
 */
final class MnemonicContainerListener implements ContainerListener {
  void addTo(Component component) {
    if (component == null || component instanceof CellRendererPane) {
      return;
    }
    if (component instanceof Container) {
      addTo((Container)component);
    }
    MnemonicWrapper.getWrapper(component);
  }

  void removeFrom(Component component) {
    if (component instanceof Container) {
      removeFrom((Container)component);
    }
  }

  @Override
  public void componentAdded(ContainerEvent event) {
    addTo(event.getChild());
  }

  @Override
  public void componentRemoved(ContainerEvent event) {
    removeFrom(event.getChild());
  }

  private void addTo(Container container) {
    if (!isAddedTo(container)) {
      container.addContainerListener(this);
      for (Component component : container.getComponents()) {
        addTo(component);
      }
    }
  }

  private void removeFrom(Container container) {
    if (isAddedTo(container)) {
      container.removeContainerListener(this);
      for (Component component : container.getComponents()) {
        removeFrom(component);
      }
    }
  }

  private boolean isAddedTo(Container container) {
    for (ContainerListener listener : container.getContainerListeners()) {
      if (listener == this) {
        return true;
      }
    }
    return false;
  }
}
