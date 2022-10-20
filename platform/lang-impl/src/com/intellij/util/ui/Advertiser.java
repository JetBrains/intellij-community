// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
import com.intellij.ui.ClickListener;
import com.intellij.ui.RelativeFont;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Advertiser {
  private final List<Item> myTexts = ContainerUtil.createLockFreeCopyOnWriteList();
  private final JPanel myComponent = new JPanel(new AdvertiserLayout());

  private final AtomicInteger myCurrentItem = new AtomicInteger(0);
  private final JLabel myTextPanel = createLabel();
  private final JLabel myNextLabel;
  
  private Color myForeground = JBUI.CurrentTheme.Advertiser.foreground();

  public Advertiser() {
    myNextLabel = new JLabel(CodeInsightBundle.message("label.next.tip"));
    myNextLabel.setFont(adFont());
    myNextLabel.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        myCurrentItem.incrementAndGet();
        updateAdvertisements();
        return true;
      }
    }.installOn(myNextLabel);

    myNextLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    myComponent.add(myTextPanel);
    myComponent.add(myNextLabel);
    myComponent.setOpaque(true);
    myComponent.setBackground(JBUI.CurrentTheme.Advertiser.background());
    myComponent.setBorder(JBUI.CurrentTheme.Advertiser.border());
  }

  private void updateAdvertisements() {
    myNextLabel.setVisible(myTexts.size() > 1);
    if (!myTexts.isEmpty()) {
      Item item = myTexts.get(myCurrentItem.get() % myTexts.size());
      item.setForLabel(myTextPanel);
    }
    else {
      myTextPanel.setText("");
      myTextPanel.setIcon(null);
      myTextPanel.setForeground(myForeground);
    }
    myComponent.revalidate();
    myComponent.repaint();
  }

  private JLabel createLabel() {
    JLabel label = new JLabel();
    label.setFont(adFont());
    label.setForeground(myForeground);
    return label;
  }

  public void showRandomText() {
    int count = myTexts.size();
    myCurrentItem.set(count > 0 ? new Random().nextInt(count) : 0);
    updateAdvertisements();
  }

  public void clearAdvertisements() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myTexts.clear();
    myCurrentItem.set(0);
    updateAdvertisements();
  }

  protected Font adFont() {
    Font font = StartupUiUtil.getLabelFont();
    RelativeFont relativeFont = RelativeFont.NORMAL.scale(JBUI.CurrentTheme.Advertiser.FONT_SIZE_OFFSET.get());
    return relativeFont.derive(font);
  }

  public void addAdvertisement(@PopupAdvertisement @NotNull String text, @Nullable Icon icon) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myTexts.add(new Item(text, icon));
    updateAdvertisements();
  }

  public void setBorder(@Nullable Border border) {
    myComponent.setBorder(border);
  }

  public void setForeground(@Nullable Color foreground) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
    if (foreground != null) {
      myForeground = foreground;
    }
    if (!myTexts.isEmpty()) {
      updateAdvertisements();
    }
  }

  public void setBackground(@Nullable Color background) {
    myComponent.setBackground(background != null ? background : JBUI.CurrentTheme.Advertiser.background());
  }

  public JComponent getAdComponent() {
    return myComponent;
  }

  public List<String> getAdvertisements() {
    return ContainerUtil.map(myTexts, item -> item.text);
  }

  // ------------------------------------------------------
  // Custom layout
  private class AdvertiserLayout implements LayoutManager {
    @Override
    public void addLayoutComponent(String name, Component comp) {}

    @Override
    public void removeLayoutComponent(Component comp) {}

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Insets i = parent.getInsets();
      Dimension size = new Dimension();
      Dimension nextButtonSize = myNextLabel.getPreferredSize();

      FontMetrics fm = myTextPanel.getFontMetrics(myTextPanel.getFont());

      for (Item item : myTexts) {
        int width = SwingUtilities.computeStringWidth(fm, item.toString());

        if (item.icon != null) {
          width += myTextPanel.getIconTextGap() + item.icon.getIconWidth();
        }

        width += nextButtonSize.width + i.left + i.right;

        int height = Math.max(fm.getHeight(), item.icon != null ? item.icon.getIconHeight() : 0) + i.top + i.bottom;
        size.width = Math.max(size.width, width);
        size.height = Math.max(size.height, Math.max(height, nextButtonSize.height));
      }

      return size;
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
      Dimension minSize = myNextLabel.getPreferredSize();
      JBInsets.addTo(minSize, parent.getInsets());
      return minSize;
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets i = parent.getInsets();
      Dimension size = parent.getSize();
      Dimension textPrefSize = myTextPanel.getPreferredSize();
      Dimension nextPrefSize = myNextLabel.getPreferredSize();

      int textWidth = (i.left + i.right + textPrefSize.width + nextPrefSize.width <= size.width) ?
                      textPrefSize.width : size.width - nextPrefSize.width - i.left - i.right;

      myTextPanel.setBounds(i.left, (size.height-textPrefSize.height) / 2, textWidth, textPrefSize.height);
      myNextLabel.setBounds(i.left + textWidth, (size.height-nextPrefSize.height) / 2, nextPrefSize.width, nextPrefSize.height);
    }
  }

  private class Item {
    private final @PopupAdvertisement String text;
    private final                     Icon   icon;

    private Item(@PopupAdvertisement @NotNull String text, @Nullable Icon icon) {
      this.text = text;
      this.icon = icon;
    }

    private void setForLabel(JLabel label) {
      label.setText(toString());
      label.setIcon(icon);
      label.setForeground(getForegroundColor());
    }

    private Color getForegroundColor() {
      return icon != null ? UIManager.getColor("Label.foreground") : myForeground;
    }

    @Override
    public @PopupAdvertisement String toString() {
      return text + "  ";
    }
  }
}
