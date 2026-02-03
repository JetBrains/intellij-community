// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.newui.HorizontalLayout;
import com.intellij.ide.plugins.newui.TagComponent;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class TagPanel extends NonOpaquePanel {
  private final LinkListener<Object> mySearchListener;

  public TagPanel(@NotNull LinkListener<Object> searchListener) {
    super(new HorizontalLayout(JBUIScale.scale(6)));
    mySearchListener = searchListener;
    setBorder(JBUI.Borders.emptyTop(2));
  }

  public void setTags(@NotNull List<String> tags) {
    if (tags.isEmpty()) {
      setVisible(false);
      return;
    }

    int newSize = tags.size();
    int oldSize = getComponentCount();
    int commonSize = Math.min(newSize, oldSize);

    for (int i = 0; i < commonSize; i++) {
      TagComponent component = (TagComponent)getComponent(i);
      component.setToolTipText(null);
      component.setText(tags.get(i));
      component.setVisible(true);
    }

    if (newSize > oldSize) {
      for (int i = oldSize; i < newSize; i++) {
        TagComponent component = new TagComponent(tags.get(i));
        add(PluginManagerConfigurable.setTinyFont(component));
        //noinspection unchecked
        component.setListener(mySearchListener, component);
      }
    }
    else if (newSize < oldSize) {
      for (int i = newSize; i < oldSize; i++) {
        getComponent(i).setVisible(false);
      }
    }

    setVisible(true);
  }

  public void setFirstTagTooltip(@Nullable @Tooltip String text) {
    if (getComponentCount() > 0) {
      ((JComponent)getComponent(0)).setToolTipText(text);
    }
  }

  @Override
  public int getBaseline(int width, int height) {
    int count = getComponentCount();
    for (int i = 0; i < count; i++) {
      Component component = getComponent(i);
      if (component.isVisible()) {
        Dimension size = component.getPreferredSize();
        return component.getY() + component.getBaseline(size.width, size.height);
      }
    }
    return -1;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleTagPanel();
      accessibleContext.setAccessibleName(IdeBundle.message("plugins.configurable.plugin.details.tags.panel.accessible.name"));
    }
    return accessibleContext;
  }

  protected class AccessibleTagPanel extends AccessibleJPanel {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibilityUtils.GROUPED_ELEMENTS;
    }
  }
}