// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.DynamicBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.openapi.options.ConfigurableTreeRenderer;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableTreeRenderer extends AncestorListenerAdapter implements ConfigurableTreeRenderer, Consumer<Integer> {
  private final CountComponent myCountLabel = new CountComponent();
  private final JLabel myExtraLabel = new JLabel();
  private final JPanel myPanel = new NonOpaquePanel() {
    @Override
    public int getBaseline(int width, int height) {
      return myCountLabel.getBaseline(width, height);
    }
  };

  private PluginUpdatesService myService;
  private SimpleTree myTree;
  private String myCountValue;

  public PluginManagerConfigurableTreeRenderer() {
    myPanel.setLayout(new BorderLayout());
    myPanel.add(myExtraLabel, BorderLayout.WEST);
    myPanel.add(myCountLabel, BorderLayout.EAST);
  }

  @Nullable
  private static Icon getExtraIcon() {
    return DynamicBundle.LanguageBundleEP.EP_NAME.hasAnyExtensions() ? AllIcons.Plugins.Hieroglyph : null;
  }

  @Nullable
  @Override
  public Pair<Component, Layout> getDecorator(@NotNull SimpleTree tree, @Nullable UnnamedConfigurable configurable, boolean selected) {
    if (myTree == null) {
      myService = PluginUpdatesService.connectTreeRenderer(this);
      tree.addAncestorListener(this);
      myTree = tree;
    }

    Icon icon = getExtraIcon();
    if (icon == null && myCountValue == null) {
      return null;
    }

    myExtraLabel.setIcon(icon);
    myExtraLabel.setVisible(icon != null);

    myCountLabel.setText(myCountValue);
    myCountLabel.setSelected(selected);
    myCountLabel.setVisible(myCountValue != null);

    myExtraLabel.setBackground(myCountLabel.getBackground());

    return Pair.create(myPanel, (renderer, bounds, text, right, textBaseline) -> {
      myPanel.doLayout();
      myPanel.revalidate();
      Dimension size = renderer.getPreferredSize();
      int x = right.x - JBUIScale.scale(2) + (right.width - size.width) / 2;
      if (icon != null && myCountValue != null) {
        x -= myCountLabel.getPreferredSize().width / 2;
      }
      int y = bounds.y + textBaseline - renderer.getBaseline(size.width, size.height);
      if (myCountValue == null) {
        y = bounds.y + (bounds.height - size.height) / 2;
      }
      renderer.setBounds(x, y, size.width, size.height);
    });
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