/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.singleRow.CompressibleSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author pegov
 */
public class JBEditorTabs extends JBTabsImpl {
  public static final String TABS_ALPHABETICAL_KEY = "tabs.alphabetical";

  /**
   * @Deprecated unused in current realization. use {@link #tabPainter}.
   */
  @Deprecated
  protected JBEditorTabsPainter myDefaultPainter = new DefaultEditorTabsPainter(this);
  protected final JBTabPainter tabPainter = createTabPainter();

  public JBEditorTabs(@Nullable Project project, @NotNull ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
    Registry.get(TABS_ALPHABETICAL_KEY).addListener(new RegistryValueListener.Adapter() {

      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        ApplicationManager.getApplication().invokeLater(() -> {
          resetTabsCache();
          relayout(true, false);
        });
      }
    }, parent);
  }

  protected JBTabPainter createTabPainter() {
    return new JBDefaultTabPainter();
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    if (!UISettings.getInstance().getHideTabsIfNeed() && supportsCompression()) {
      return new CompressibleSingleRowLayout(this);
    }
    else if (ApplicationManager.getApplication().isInternal() || Registry.is("editor.use.scrollable.tabs")) {
      return new ScrollableSingleRowLayout(this);
    }
    return super.createSingleRowLayout();
  }

  @Override
  public boolean supportsCompression() {
    return true;
  }

  @Nullable
  public Rectangle getSelectedBounds() {
    TabLabel label = getSelectedLabel();
    return label != null ? label.getBounds() : null;
  }

  @Override
  public boolean isEditorTabs() {
    return true;
  }

  @Override
  public boolean isGhostsAlwaysVisible() {
    return false;
  }

  @Override
  public boolean useSmallLabels() {
    return UISettings.getInstance().getUseSmallLabelsOnTabs();
  }

  @Override
  public boolean useBoldLabels() {
    return SystemInfo.isMac && Registry.is("ide.mac.boldEditorTabs");
  }

  @Override
  protected void doPaintInactive(Graphics2D g2d,
                                 TabInfo info) {
    final TabLabel label = myInfo2Label.get(info);
    if (label == null) return;

    Rectangle rect = fixedBounds(label.getBounds());
    final Color tabColor = label.getInfo().getTabColor();

    tabPainter.paintTab(g2d, rect, tabColor, isHoveredTab(label));
  }


  @Override
  protected void doPaintSelected(Graphics2D g2d,
                                 TabInfo info) {
    final TabLabel label = myInfo2Label.get(info);
    if (label == null) return;

    Rectangle rect = fixedBounds(label.getBounds());
    final Color tabColor = label.getInfo().getTabColor();
    tabPainter.paintSelectedTab(g2d, rect, tabColor, getPosition(), isActiveTab(info), isHoveredTab(label));
  }

  @NotNull
  private Rectangle fixedBounds(Rectangle effectiveBounds) {
    return fixedBounds(effectiveBounds, getTabsBorder().getEffectiveBorder());
  }
  private Rectangle fixedBounds(Rectangle effectiveBounds, Insets insets) {
    int _x = effectiveBounds.x + insets.left;
    int _y = effectiveBounds.y + insets.top;
    int _width = effectiveBounds.width - insets.left - insets.right + (getTabsPosition() == JBTabsPosition.right ? 1 : 0);
    int _height = effectiveBounds.height - insets.top - insets.bottom;

    return new Rectangle(_x, _y, _width, _height);
  }

  protected boolean isActiveTab(TabInfo info) {
    return Utils.Companion.isFocusOwner(this);
  }

  @Override
  public boolean isAlphabeticalMode() {
    return Registry.is(TABS_ALPHABETICAL_KEY);
  }

  public static void setAlphabeticalMode(boolean on) {
    Registry.get(TABS_ALPHABETICAL_KEY).setValue(on);
  }

  @Override
  protected void doPaintBackground(Graphics2D g2d, Rectangle backgroundRect) {
    //TODO add border instead of background

    List<TabInfo> visibleInfos = getVisibleInfos();
    final boolean vertical = getTabsPosition() == JBTabsPosition.left || getTabsPosition() == JBTabsPosition.right;

    Insets insets = getTabsBorder().getEffectiveBorder();

    int minOffset = vertical ? getHeight(): getWidth();
    int maxOffset = 0;
    int maxLength = 0;

    for (int i = visibleInfos.size() - 1; i >= 0; i--) {
      TabInfo visibleInfo = visibleInfos.get(i);
      TabLabel tabLabel = myInfo2Label.get(visibleInfo);
      Rectangle r = tabLabel.getBounds();
      if (r.width == 0 || r.height == 0) continue;
      minOffset = Math.min(vertical ? r.y : r.x, minOffset);
      maxOffset = Math.max(vertical ? r.y + r.height : r.x + r.width, maxOffset);
      maxLength = vertical ? r.width : r.height;
    }

    Rectangle r2 = new Rectangle(0, 0, getWidth(), getHeight());

    Rectangle beforeTabs;
    Rectangle afterTabs;
    if (vertical) {
      int x = r2.x + insets.left;
      int width = maxLength - insets.left - insets.right;

      if (getTabsPosition() == JBTabsPosition.right) {
        x = r2.width - width - insets.left;
      }

      beforeTabs = new Rectangle(x, insets.top, width, minOffset - insets.top);
      afterTabs = new Rectangle(x, maxOffset, width, r2.height - maxOffset - insets.top - insets.bottom);
    } else {
      int y = r2.y + insets.top;
      int height = maxLength - insets.top - insets.bottom;
      if (getTabsPosition() == JBTabsPosition.bottom) {
        y = r2.height - height - insets.top;
      }

      beforeTabs = new Rectangle(insets.left, y, minOffset, height);
      afterTabs = new Rectangle(maxOffset, y, r2.width - maxOffset - insets.left - insets.right, height);
    }

    tabPainter.fillBackground(g2d, backgroundRect);
    tabPainter.fillBeforeAfterTabs(g2d, beforeTabs, afterTabs);
  }

  /**
   * @deprecated You should move the painting logic to an implementation of {@link JBTabPainter} interface }
   */
  @Deprecated
  protected Color getEmptySpaceColor() {
    return tabPainter.getBackgroundColor();
  }

/*  @Override
  protected void doPaintBackground(Graphics2D g2d, Rectangle rect) {
   // Rectangle rect = new Rectangle(0, 0, getWidth(), getHeight());
    Insets insets = new JBInsets(3, 3, 3, 3);

    Rectangle bkg = fixedBounds(rect, insets);

    g2d.setColor(JBColor.RED);
    g2d.fillRect(rect.x, rect.y, rect.width, rect.height);*//*

    tabPainter.fillBackground(g2d, new Rectangle(0, 0, getWidth(), getHeight()));
  }*/
}
