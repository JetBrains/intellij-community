// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.ui.ErrorLabel;
import com.intellij.ui.GroupedElementsRenderer;

import javax.swing.*;
import java.awt.*;

public class FacetsTreeCellRenderer extends GroupedElementsRenderer.Tree {
  @Override
  protected JComponent createItemComponent() {
    myTextLabel = new ErrorLabel();
    return myTextLabel;
  }

  @Override
  protected void layout() {
    myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
    myRendererComponent.add(getItemComponent(), BorderLayout.CENTER);
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row,
                                                boolean hasFocus) {
    if (value instanceof MasterDetailsComponent.MyNode node) {
      final NamedConfigurable<?> configurable = node.getConfigurable();
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
