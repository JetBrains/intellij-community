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
  private final JPanel myPanel = new NonOpaquePanel();

  private PluginUpdatesService myService;
  private SimpleTree myTree;
  private String myCountValue;

  public PluginManagerConfigurableTreeRenderer() {
    myPanel.setLayout(new BorderLayout(getHGap(), 0));
    myPanel.add(myCountLabel, BorderLayout.WEST);
    myPanel.add(myExtraLabel, BorderLayout.EAST);
  }

  @Nullable
  private static Icon getExtraIcon() {
    return DynamicBundle.LanguageBundleEP.EP_NAME.hasAnyExtensions() ? AllIcons.General.LocalizationSettings : null;
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

    myCountLabel.setText(StringUtil.defaultIfEmpty(myCountValue, "0")); // for correct calculate baseline
    myCountLabel.setSelected(selected);
    myExtraLabel.setIcon(icon);
    myExtraLabel.setBackground(myCountLabel.getBackground());

    Component component;
    if (icon != null && myCountValue != null) {
      component = myPanel;
    }
    else if (icon == null) {
      component = myCountLabel;
    }
    else {
      component = myExtraLabel;
    }

    return Pair.create(component, (renderer, bounds, text, right, textBaseline) -> {
      Dimension size = renderer.getPreferredSize();
      int x = right.x - JBUIScale.scale(2) + (right.width - size.width) / 2;
      if (icon != null && myCountValue != null) {
        x -= getHGap() + myExtraLabel.getPreferredSize().width / 2;
      }
      int y = bounds.y + textBaseline - myCountLabel.getBaseline(size.width, size.height);
      renderer.setBounds(x, y, size.width, size.height);
      renderer.doLayout();
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

  private static int getHGap() {
    return JBUIScale.scale(3);
  }
}