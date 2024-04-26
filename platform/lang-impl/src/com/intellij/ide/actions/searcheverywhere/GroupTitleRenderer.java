// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ui.CellRendererPanel;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

/**
 * Should be moved into common place, used not only in SE
 * @deprecated Use {@link com.intellij.ui.GroupHeaderSeparator} instead. (see UX-2329)
 */
@ApiStatus.Internal
@Deprecated
public final class GroupTitleRenderer extends CellRendererPanel {

  private final SimpleColoredComponent titleLabel = new SimpleColoredComponent();

  public GroupTitleRenderer() {
    setLayout(new BorderLayout());
    SeparatorComponent separatorComponent = new SeparatorComponent(
      titleLabel.getPreferredSize().height / 2, JBUI.CurrentTheme.BigPopup.listSeparatorColor(), null);

    JPanel topPanel = JBUI.Panels.simplePanel(5, 0)
      .addToCenter(separatorComponent)
      .addToLeft(titleLabel)
      .withBorder(JBUI.Borders.empty(1, 7))
      .withBackground(ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Popup.BACKGROUND : UIUtil.getListBackground());
    add(topPanel, BorderLayout.NORTH);
  }

  public GroupTitleRenderer withDisplayedData(@Nls String title, Component itemContent) {
    titleLabel.clear();
    titleLabel.append(title, SEResultsListFactory.SMALL_LABEL_ATTRS);
    Component prevContent = ((BorderLayout)getLayout()).getLayoutComponent(BorderLayout.CENTER);
    if (prevContent != null) {
      remove(prevContent);
    }
    add(itemContent, BorderLayout.CENTER);
    accessibleContext = itemContent.getAccessibleContext();

    return this;
  }
}
