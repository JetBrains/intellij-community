// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.images.thumbnail.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.intellij.images.options.OptionsManager;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.NotNull;

public final class ToggleFileNameAction extends ToggleAction {
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        ImageComponentDecorator decorator = e.getData(ImageComponentDecorator.DATA_KEY);
        return decorator != null && decorator.isEnabledForActionPlace(e.getPlace()) && decorator.isFileNameVisible();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        ImageComponentDecorator decorator = e.getData(ImageComponentDecorator.DATA_KEY);
        if (decorator != null && decorator.isEnabledForActionPlace(e.getPlace())) {
            decorator.setFileNameVisible(state);
            OptionsManager.getInstance().getOptions().getEditorOptions().setFileNameVisible(state);
        }
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
        super.update(e);
        ImageComponentDecorator decorator = e.getData(ImageComponentDecorator.DATA_KEY);
        e.getPresentation().setEnabled(decorator != null && decorator.isEnabledForActionPlace(e.getPlace()));
        e.getPresentation().setText(isSelected(e) ? IdeBundle.message("action.text.hide.file.name") : IdeBundle.message("action.text.show.file.name"));
    }
}
