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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.GroupedElementsRenderer;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class FacetsTreeCellRenderer extends GroupedElementsRenderer.Tree {
  @Override
  protected JComponent createItemComponent() {
    myTextLabel = new ErrorLabel();
    return myTextLabel;
  }

  @Override
  protected void layout() {
    myRendererComponent.setOpaqueActive(false);
    myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
    myRendererComponent.add(myComponent, BorderLayout.CENTER);
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row,
                                                boolean hasFocus) {
    if (value instanceof MasterDetailsComponent.MyNode) {
      final MasterDetailsComponent.MyNode node = (MasterDetailsComponent.MyNode)value;
      final NamedConfigurable configurable = node.getConfigurable();
      if (configurable != null) {
        final Icon icon = configurable.getIcon(expanded);
        final boolean showSeparator = configurable instanceof FrameworkDetectionConfigurable;
        final JComponent component = configureComponent(node.getDisplayName(), null, icon, icon, selected, showSeparator, null, -1);

        myTextLabel.setOpaque(selected);
        return component;
      }
    }
    return myRendererComponent;
  }
}
