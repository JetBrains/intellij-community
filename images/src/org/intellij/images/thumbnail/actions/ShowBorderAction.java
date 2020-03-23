// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.thumbnail.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ui.UIUtil;
import org.intellij.images.ui.ImageComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

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
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance().setValue(PROP_NAME, state);
        Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
        ImageComponent imageComponent = null;
        if (component instanceof ImageComponent) {
            imageComponent = (ImageComponent) component;
        } else if (component instanceof JComponent) {
            imageComponent = UIUtil.findComponentOfType((JComponent) component, ImageComponent.class);
        }

        if (imageComponent != null) {
            imageComponent.setBorderVisible(state);
        }
    }
}
