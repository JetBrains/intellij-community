// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.openapi.options.ConfigurableTreeRenderer;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableTreeRenderer extends AncestorListenerAdapter implements ConfigurableTreeRenderer, Consumer<Integer> {
  private final CountComponent myCountLabel = new CountComponent();
  private PluginUpdatesService myService;
  private SimpleTree myTree;
  private String myCountValue;

  @Nullable
  @Override
  public Pair<Component, Layout> getDecorator(@NotNull SimpleTree tree, @Nullable UnnamedConfigurable configurable, boolean selected) {
    if (!Registry.is("show.new.plugin.page", false) && !Registry.is("show.new.layout.plugin.page", false)) {
      return null;
    }
    if (myTree == null) {
      myService = PluginUpdatesService.connectTreeRenderer(this);
      tree.addAncestorListener(this);
      myTree = tree;
    }
    if (myCountValue != null) {
      myCountLabel.setText(myCountValue);
      myCountLabel.setSelected(selected);

      return Pair.create(myCountLabel, (renderer, bounds, text, right, textBaseline) -> {
        Dimension size = renderer.getPreferredSize();
        int x = right.x - JBUIScale.scale(2) + (right.width - size.width) / 2;
        int y = bounds.y + textBaseline - renderer.getBaseline(size.width, size.height);
        renderer.setBounds(x, y, size.width, size.height);
      });
    }
    return null;
  }

  @Override
  public void ancestorRemoved(AncestorEvent event) {
    myService.dispose();
  }

  @Override
  public void accept(Integer countValue) {
    String oldCountValue = myCountValue;
    myCountValue = countValue == null || countValue <= 0 ? null : countValue.toString();
    if (myTree != null && !StringUtil.equals(oldCountValue, myCountValue)) {
      myTree.repaint();
    }
  }
}