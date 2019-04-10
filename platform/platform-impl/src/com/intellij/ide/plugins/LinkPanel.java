// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.newui.CellPluginComponent;
import com.intellij.ide.plugins.newui.TextHorizontalLayout;
import com.intellij.ui.components.labels.LinkLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class LinkPanel {
  private final JLabel myTextLabel = new JLabel();
  private final JLabel myLinkLabel = createLink();
  private Runnable myRunnable;

  public LinkPanel(@NotNull JPanel parent) {
    myTextLabel.setForeground(CellPluginComponent.GRAY_COLOR);
    parent.add(myTextLabel, TextHorizontalLayout.FIX_LABEL);
    parent.add(myLinkLabel);
  }

  @NotNull
  private JLabel createLink() {
    LinkLabel<Object> linkLabel = new LinkLabel<>(null, AllIcons.Ide.External_link_arrow, (__, ___) -> myRunnable.run());
    linkLabel.setIconTextGap(0);
    linkLabel.setHorizontalTextPosition(SwingConstants.LEFT);
    return linkLabel;
  }

  public void show(@NotNull String text, @Nullable Runnable linkCallback) {
    myRunnable = linkCallback;

    if (linkCallback == null) {
      myTextLabel.setText(text);
      myTextLabel.setVisible(true);

      myLinkLabel.setVisible(false);
    }
    else {
      myTextLabel.setVisible(false);

      myLinkLabel.setText(text);
      myLinkLabel.setVisible(true);
    }
  }

  public void hide() {
    myRunnable = null;
    myTextLabel.setVisible(false);
    myLinkLabel.setVisible(false);
  }
}