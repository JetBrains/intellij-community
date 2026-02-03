// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.thumbnail.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.intellij.images.editor.actionSystem.ImageEditorActionUtil;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class ShowBorderAction extends ToggleAction implements DumbAware {
  public static final String PROP_NAME = "ImagePlugin.borderVisible";

  public static boolean isBorderVisible() {
    return PropertiesComponent.getInstance().getBoolean(PROP_NAME, false);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isBorderVisible();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    ImageComponentDecorator decorator = ImageEditorActionUtil.getImageComponentDecorator(e);
    PropertiesComponent.getInstance().setValue(PROP_NAME, state);
    if (decorator != null) {
      decorator.setBorderVisible(state);
    }
  }
}
