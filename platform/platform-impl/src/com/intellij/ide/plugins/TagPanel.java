// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.plugins.newui.HorizontalLayout;
import com.intellij.ide.plugins.newui.TagComponent;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class TagPanel extends NonOpaquePanel {
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
      component.setText(tags.get(i));
      component.setVisible(true);
    }

    if (newSize > oldSize) {
      for (int i = oldSize; i < newSize; i++) {
        TagComponent component = new TagComponent(tags.get(i));
        add(PluginManagerConfigurableNew.installTiny(component));
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
}