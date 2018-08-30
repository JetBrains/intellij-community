// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.Function;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class PluginsGroupComponentWithProgress extends PluginsGroupComponent {
  private AsyncProcessIcon myIcon = new CenteredIcon();

  public PluginsGroupComponentWithProgress(@NotNull LayoutManager layout,
                                           @NotNull EventHandler eventHandler,
                                           @NotNull LinkListener<IdeaPluginDescriptor> listener,
                                           @NotNull LinkListener<String> searchListener,
                                           @NotNull Function<IdeaPluginDescriptor, CellPluginComponent> function) {
    super(layout, eventHandler, listener, searchListener, function);
    myIcon.setOpaque(false);
    myIcon.setPaintPassiveIcon(false);
    add(myIcon);
    myIcon.resume();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      dispose();
    }
  }

  @Override
  public void doLayout() {
    super.doLayout();
    if (myIcon != null) {
      myIcon.updateLocation(this);
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myIcon != null) {
      myIcon.updateLocation(this);
    }
  }

  public void startLoading() {
    if (myIcon != null) {
      myIcon.setVisible(true);
      myIcon.resume();
      doLayout();
      revalidate();
      repaint();
    }
  }

  public void stopLoading() {
    if (myIcon != null) {
      myIcon.suspend();
      myIcon.setVisible(false);
      doLayout();
      revalidate();
      repaint();
    }
  }

  public void dispose() {
    if (myIcon != null) {
      remove(myIcon);
      Disposer.dispose(myIcon);
      myIcon = null;
    }
  }

  private static class CenteredIcon extends AsyncProcessIcon.Big {
    public CenteredIcon() {
      super("Loading");
    }

    @NotNull
    @Override
    protected Rectangle calculateBounds(@NotNull JComponent container) {
      Dimension size = container.getSize();
      Dimension iconSize = getPreferredSize();
      return new Rectangle((size.width - iconSize.width) / 2, (size.height - iconSize.height) / 2, iconSize.width, iconSize.height);
    }
  }
}