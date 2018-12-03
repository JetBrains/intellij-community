// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.openapi.options.ConfigurableTreeRenderer;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.roots.ui.configuration.SidePanelCountLabel;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableTreeRenderer implements ConfigurableTreeRenderer {
  private final SidePanelCountLabel myCountLabel = new SidePanelCountLabel() {
    @Override
    public void paint(Graphics g) {
      g.translate(7, 0);
      super.paint(g);
    }
  };

  private SimpleTree myTree;
  private String myCountValue;

  @Nullable
  @Override
  public Component getRightDecorator(@NotNull SimpleTree tree, @Nullable UnnamedConfigurable configurable, boolean selected) {
    if (!Registry.is("show.new.plugin.page", false)) {
      return null;
    }
    if (myTree == null) {
      PluginUpdatesService.connectTreeRenderer(tree, countValue -> {
        String oldCountValue = myCountValue;
        myCountValue = countValue == null || countValue <= 0 ? null : countValue.toString();
        if (myTree != null && !StringUtil.equals(oldCountValue, myCountValue)) {
          myTree.repaint();
        }
      });
      myTree = tree;
    }
    if (myCountValue != null) {
      myCountLabel.setText(myCountValue);
      myCountLabel.setSelected(selected);
      return myCountLabel;
    }
    return null;
  }
}