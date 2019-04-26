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

import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

/**
 * @author Sergey.Malenkov
 */
final class MnemonicContainerListener implements ContainerListener {
  void addTo(Component component) {
    JBTreeTraverser<Component> traverser = UIUtil.uiTraverser(component)
      .expandAndFilter(o -> !(o instanceof CellRendererPane));
    for (Component c : traverser) {
      if (c instanceof Container) ((Container)c).addContainerListener(this);
      MnemonicWrapper.getWrapper(component);
    }
  }

  void removeFrom(Component component) {
    JBTreeTraverser<Component> traverser = UIUtil.uiTraverser(component)
      .expandAndFilter(o -> !(o instanceof CellRendererPane));
    for (Container c : traverser.traverse().filter(Container.class)) {
      c.removeContainerListener(this);
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
}
