// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.openapi.options.ConfigurableTreeRenderer;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
  private PluginUpdatesService myService;
  private SimpleTree myTree;
  private String myCountValue;

  @Nullable
  @Override
  public Pair<Component, Layout> getDecorator(@NotNull SimpleTree tree, @Nullable UnnamedConfigurable configurable, boolean selected) {
    if (!Registry.is("show.new.plugin.page", false)) {
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
        int x = right.x - JBUI.scale(2) + (right.width - size.width) / 2;
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

  private static class CountComponent extends JLabel {
    @SuppressWarnings("UseJBColor")
    private final Color myOvalColor = JBColor.namedColor("Counter.background", new Color(0xCC9AA7B0, true));

    private CountComponent() {
      setBorder(null);
      setFont(UIUtil.getLabelFont(SystemInfo.isMac || (SystemInfo.isLinux && (UIUtil.isUnderIntelliJLaF() || UIUtil.isUnderDarcula()))
                                  ? UIUtil.FontSize.SMALL
                                  : UIUtil.FontSize.NORMAL));
      setForeground(JBColor.namedColor("Counter.foreground", new JBColor(0xFFFFFF, 0x3E434D)));
      setHorizontalAlignment(CENTER);
      setHorizontalTextPosition(CENTER);
    }

    public void setSelected(boolean selected) {
      setBackground(selected ? UIUtil.getTreeSelectionBackground(true) : UIUtil.SIDE_PANEL_BACKGROUND);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.max(size.width, getTextOffset() + getOvalWidth()), Math.max(size.height, getOvalHeight()));
    }

    @Override
    protected void paintComponent(Graphics g) {
      int corner = JBUI.scale(14);
      int ovalWidth = getOvalWidth();
      int ovalHeight = getOvalHeight();
      int width = getWidth();
      int height = getHeight();

      GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

      g.setColor(getBackground());
      g.fillRect(0, 0, width, height);

      g.setColor(myOvalColor);
      g.fillRoundRect(getTextOffset() + (width - ovalWidth) / 2, (height - ovalHeight) / 2, ovalWidth, ovalHeight, corner, corner);

      config.restore();

      super.paintComponent(g);
    }

    private int getOvalWidth() {
      return JBUI.scale(getText().length() == 1 ? 16 : 20);
    }

    private int getTextOffset() {
      String text = getText();
      return text.equals("1") || text.equals("3") || text.equals("4") ? 1 : 0;
    }

    private static int getOvalHeight() {
      return JBUI.scale(14);
    }
  }
}