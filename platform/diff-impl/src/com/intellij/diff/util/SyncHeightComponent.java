// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.openapi.util.Conditions;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class SyncHeightComponent extends JPanel {
  private SyncHeightComponent(@NotNull SyncHeightHolder syncHeightHolder, @Nullable JComponent component) {
    super(new SyncHeightLayout(syncHeightHolder, component));
    if (component != null) add(component);
  }


  @NotNull
  public static List<JComponent> createSyncHeightComponents(@NotNull final List<JComponent> components) {
    if (!ContainerUtil.exists(components, Conditions.notNull())) return components;

    SyncHeightHolder syncHeightHolder = new SyncHeightHolder(components);

    List<JComponent> result = new ArrayList<>();
    for (JComponent component : components) {
      result.add(new SyncHeightComponent(syncHeightHolder, component));
    }
    return result;
  }

  public static void invalidateByChild(@NotNull JComponent component) {
    SyncHeightComponent syncComponent = UIUtil.getParentOfType(SyncHeightComponent.class, component);
    if (syncComponent == null) return;
    SyncHeightLayout syncHeightLayout = ObjectUtils.tryCast(syncComponent.getLayout(), SyncHeightLayout.class);
    if (syncHeightLayout == null) return;

    syncHeightLayout.getSyncHeightHolder().revalidateAll();
  }

  @NotNull
  private static Dimension getPreferredSize(@Nullable Component component) {
    return component != null && component.isVisible() ? component.getPreferredSize() : new Dimension();
  }


  private static class SyncHeightHolder {
    private final @NotNull List<? extends JComponent> mySyncComponents;

    private int myLastPreferredHeight;

    private SyncHeightHolder(@NotNull List<? extends JComponent> components) {
      mySyncComponents = components;
      myLastPreferredHeight = preferredHeight();
    }

    public int preferredHeight() {
      int totalHeight = 0;

      for (JComponent component : mySyncComponents) {
        Dimension size = getPreferredSize(component);
        totalHeight = Math.max(size.height, totalHeight);
      }

      if (myLastPreferredHeight != totalHeight) {
        myLastPreferredHeight = totalHeight;
        SwingUtilities.invokeLater(this::revalidateAll);
      }

      return totalHeight;
    }

    public void revalidateAll() {
      for (JComponent component : mySyncComponents) {
        if (component != null) component.revalidate();
      }
    }
  }

  @ApiStatus.Internal
  public static class SyncHeightLayout extends AbstractLayoutManager {
    private final @NotNull SyncHeightHolder mySyncHeightHolder;
    private final @Nullable JComponent myComponent;

    SyncHeightLayout(@NotNull SyncHeightHolder syncHeightHolder, @Nullable JComponent component) {
      mySyncHeightHolder = syncHeightHolder;
      myComponent = component;
    }

    private @NotNull SyncHeightHolder getSyncHeightHolder() {
      return mySyncHeightHolder;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int syncHeight = mySyncHeightHolder.preferredHeight();
      int width = getPreferredSize(myComponent).width;
      return new Dimension(width, syncHeight);
    }

    @Override
    public void layoutContainer(@NotNull Container parent) {
      if (myComponent == null) return;

      Dimension preferredSize = getPreferredSize(myComponent);

      int width = parent.getWidth();
      int height = parent.getHeight();
      myComponent.setBounds(0, 0, width, Math.min(height, preferredSize.height));
    }
  }
}
