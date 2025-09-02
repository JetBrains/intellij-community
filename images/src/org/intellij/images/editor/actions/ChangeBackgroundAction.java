// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.picker.ColorListener;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.intellij.images.editor.actionSystem.ImageEditorActionUtil;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

final class ChangeBackgroundAction extends DumbAwareAction {
  private final MyBackgroundIcon myIcon = new MyBackgroundIcon();

  ChangeBackgroundAction() {
    getTemplatePresentation().setIcon(myIcon);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (e.getInputEvent() == null) return;

    Component component = e.getInputEvent().getComponent();

    ImageComponentDecorator decorator = ImageEditorActionUtil.getImageComponentDecorator(e);
    if (component != null && decorator != null) {
      ColorChooserService.getInstance().showPopup(e.getProject(), null, null, new ColorListener() {
        @Override
        public void colorChanged(Color color, Object source) {
          myIcon.color = color;
          component.repaint();
          decorator.setEditorBackground(color);
        }
      });
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(Registry.is("ide.images.change.background.action.enabled", false));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static class MyBackgroundIcon implements Icon {
    Color color = JBColor.background();

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
      g.setColor(color);
      g.fillOval(x + 1, y + 1, 14, 14);
      g.setColor(ColorUtil.isDark(color) ? Gray.xFF.withAlpha(80) : Gray.x00.withAlpha(80));
      g.drawOval(x + 1, y + 1, 14, 14);
      config.restore();
    }

    @Override
    public int getIconWidth() {
      return JBUI.scale(16);
    }

    @Override
    public int getIconHeight() {
      return JBUI.scale(16);
    }
  }
}
