// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.newui.ListPluginComponent;
import com.intellij.ui.components.labels.LinkLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class LinkPanel {
  private final JLabel myTextLabel = new JLabel();
  private final JLabel myLinkLabel;
  private Runnable myRunnable;

  public LinkPanel(@NotNull JPanel parent, boolean icon, boolean tiny, @Nullable Object labelConstraints, @Nullable Object linkConstraints) {
    myLinkLabel = createLink(icon);
    myTextLabel.setForeground(ListPluginComponent.GRAY_COLOR);
    if (tiny) {
      PluginManagerConfigurable.setTinyFont(myLinkLabel);
      PluginManagerConfigurable.setTinyFont(myTextLabel);
    }
    parent.add(myTextLabel, labelConstraints);
    parent.add(myLinkLabel, linkConstraints);
  }

  public LinkPanel(@NotNull JPanel parent, boolean tiny) {
    this(parent, true, tiny, null, null);
  }

  @NotNull
  private JLabel createLink(boolean icon) {
    LinkLabel<Object> linkLabel = new LinkLabel<>(null, icon ? AllIcons.Ide.External_link_arrow : null, (__, ___) -> myRunnable.run());
    linkLabel.setIconTextGap(0);
    linkLabel.setHorizontalTextPosition(SwingConstants.LEFT);
    return linkLabel;
  }

  public void show(@NotNull @Nls String text, @Nullable Runnable linkCallback) {
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