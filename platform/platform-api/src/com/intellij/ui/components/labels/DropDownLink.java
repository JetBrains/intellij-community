// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.labels;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLabelUI;
import java.awt.*;
import java.util.List;

public class DropDownLink<T> extends LinkLabel<Object> {
  private T chosenItem;

  public DropDownLink(@NotNull T value, @NotNull Runnable clickAction) {
    super(value.toString(), AllIcons.General.LinkDropTriangle, (s, d) -> clickAction.run());
    chosenItem = value;
    init();
  }

  public DropDownLink(@NotNull T initialItem, @NotNull List<T> items, @Nullable Consumer<T> itemChosenAction, boolean updateLabel) {
    super(initialItem.toString(), AllIcons.General.LinkDropTriangle);
    chosenItem = initialItem;
    IPopupChooserBuilder<T> popupBuilder = JBPopupFactory.getInstance().createPopupChooserBuilder(items).
      setRenderer(new LinkCellRenderer<>(this)).
      setItemChosenCallback(t -> {
        chosenItem = t;
        if (updateLabel) {
          setText(t.toString());
        }

        if (itemChosenAction != null) {
          itemChosenAction.consume(t);
        }
      });

    setListener((s, d) -> {
      Point showPoint = new Point(0, getHeight() + JBUI.scale(4));
      popupBuilder.createPopup().show(new RelativePoint(this, showPoint));
    }, null);

    init();
  }

  private void init() {
    setIconTextGap(JBUI.scale(1));
    setHorizontalAlignment(SwingConstants.LEADING);
    setHorizontalTextPosition(SwingConstants.LEADING);

    setUI(new MetalLabelUI() {
      @Override
      protected String layoutCL(JLabel label, FontMetrics fontMetrics, String text, Icon icon,
                                Rectangle viewR, Rectangle iconR, Rectangle textR) {
        String result = super.layoutCL(label, fontMetrics, text, icon, viewR, iconR, textR);
        iconR.y += JBUI.scale(1);
        return result;
      }
    });
  }

  public T getChosenItem() {
    return chosenItem;
  }

  private static class LinkCellRenderer<T> extends JLabel implements ListCellRenderer<T> {
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
    public Component getListCellRendererComponent(JList<? extends T> list, T value, int index, boolean isSelected, boolean cellHasFocus) {
      setText(value.toString());
      setEnabled(list.isEnabled());
      setOpaque(true);

      setBackground(isSelected ? list.getSelectionBackground() : UIManager.getColor("Label.background"));
      setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

      return this;
    }
  }
}
