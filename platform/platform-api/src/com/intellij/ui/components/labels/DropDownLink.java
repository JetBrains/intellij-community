// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.labels;

import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.IconCache;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DropDownLink extends JPanel implements LinkListener<Object> {
  private final LinkLabel       mainLabel;
  private final LinkLabel<?>    dropLabel;
  private final List<LinkItem> dropActionsList = new ArrayList<>();

  private IPopupChooserBuilder<LinkItem> myPopupBuilder;

  public DropDownLink(@NotNull String mainText, @NotNull Runnable mainAction) {
    mainLabel = LinkLabel.create(mainText, mainAction);
    dropLabel = new LinkLabel<>(null, IconCache.getIcon("linkDropTriangle"), this);
    //dropLabel.setHoveringIcon(AllIcons.Ide.Notification.ExpandHover);

    setLayout(new DropDownLinkLayout());
  }

  public DropDownLink addDropItem(@NotNull String text, @NotNull Runnable action) {
    dropActionsList.add(new LinkItem(text, action));
    return this;
  }


  @Override
  public void addNotify() {
    add(mainLabel);

    if (!dropActionsList.isEmpty()) {
      add(dropLabel);

      myPopupBuilder = JBPopupFactory.getInstance().createPopupChooserBuilder(dropActionsList).
        setRenderer(new LinkCellRenderer(this)).
        setItemChosenCallback(i -> i.getAction().run());
    }
    super.addNotify();
  }

  @Override
  public void linkSelected(LinkLabel aSource, Object aLinkData) {
    Point showPoint = new Point(0, getHeight() + JBUI.scale(4));
    myPopupBuilder.createPopup().show(new RelativePoint(this, showPoint));
  }

  private static class LinkItem {
    private final String text;
    private final Runnable action;

    private LinkItem(@NotNull String text, @NotNull Runnable action) {
      this.text = text;
      this.action = action;
    }

    String getText() {
      return text;
    }

    Runnable getAction() {
      return action;
    }
  }

  private static class LinkCellRenderer extends JLabel implements ListCellRenderer<LinkItem> {
    private final JComponent owner;

    private LinkCellRenderer(JComponent owner) {
      this.owner = owner;
      setBorder(JBUI.Borders.empty(0, 5, 0, 10));
    }

    @Override
    public Dimension getPreferredSize() {
      return recomputeSize(super.getPreferredSize());
    }

    @Override
    public Dimension getMinimumSize() {
      return recomputeSize(super.getMinimumSize());
    }

    private Dimension recomputeSize(@NotNull Dimension size) {
      size.height = Math.max(size.height, JBUI.scale(22));
      size.width = Math.max(size.width, owner.getPreferredSize().width);
      return size;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends LinkItem> list, LinkItem value, int index, boolean isSelected, boolean cellHasFocus) {
      setText(value.getText());
      setEnabled(list.isEnabled());
      setOpaque(true);

      setBackground(isSelected ? list.getSelectionBackground() :
                    UIManager.getColor("Label.background"));
      setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

      return this;
    }
  }

  private class DropDownLinkLayout implements LayoutManager {
    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      return preferredLayoutSize(parent);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension size = mainLabel.getPreferredSize();
      if (!dropActionsList.isEmpty()) {
        Icon dropIcon = dropLabel.getIcon();
        size.width += JBUI.scale(1) + dropIcon.getIconWidth();
        size.height = Math.max(size.height, dropIcon.getIconHeight());
      }

      return size;
    }

    @Override
    public void layoutContainer(Container parent) {
      Dimension size = parent.getSize();
      Dimension prefSize = mainLabel.getPreferredSize();

      mainLabel.setBounds(0, size.height - prefSize.height, prefSize.width, prefSize.height);
      if (!dropActionsList.isEmpty()) {
        Icon dropIcon = dropLabel.getIcon();
        dropLabel.setBounds(prefSize.width + JBUI.scale(1), size.height - dropIcon.getIconHeight(),
                            dropIcon.getIconWidth(), dropIcon.getIconWidth());
      }
    }
  }
}
