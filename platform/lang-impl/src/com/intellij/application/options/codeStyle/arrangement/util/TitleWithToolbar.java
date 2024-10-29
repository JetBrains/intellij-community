// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.util;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class TitleWithToolbar extends JPanel {

  public TitleWithToolbar(@NlsContexts.BorderTitle @NotNull String title,
                          @NotNull String actionGroupId,
                          @NotNull String place,
                          @NotNull JComponent targetComponent)
  {
    super(new GridBagLayout());
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup group = (ActionGroup)actionManager.getAction(actionGroupId);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(place, group, true);
    actionToolbar.setTargetComponent(targetComponent);
    actionToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);

    JLabel label = new JLabel(title.startsWith("<html>") ? title : UIUtil.replaceMnemonicAmpersand(title));
    label.setLabelFor(targetComponent);

    GridBag gb = new GridBag().nextLine();
    add(label, gb.anchor(GridBagConstraints.WEST));
    add(new JPanel(), gb.next().weightx(1).fillCellHorizontally());
    add(actionToolbar.getComponent(), gb.next().anchor(GridBagConstraints.CENTER));

    setBorder(JBUI.Borders.empty(12, ArrangementConstants.HORIZONTAL_PADDING, 0, 0));
  }
}
