/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.intellij.images.options.OptionsManager;
import org.intellij.images.ui.ImageComponentDecorator;

public final class ToggleFileNameAction extends ToggleAction {
    public boolean isSelected(AnActionEvent e) {
        ImageComponentDecorator decorator = ImageComponentDecorator.DATA_KEY.getData(e.getDataContext());
        return decorator != null && decorator.isEnabledForActionPlace(e.getPlace()) && decorator.isFileNameVisible();
    }

    public void setSelected(AnActionEvent e, boolean state) {
        ImageComponentDecorator decorator = ImageComponentDecorator.DATA_KEY.getData(e.getDataContext());
        if (decorator != null && decorator.isEnabledForActionPlace(e.getPlace())) {
            decorator.setFileNameVisible(state);
            OptionsManager.getInstance().getOptions().getEditorOptions().setFileNameVisible(state);
        }
    }

    public void update(final AnActionEvent e) {
        super.update(e);
        ImageComponentDecorator decorator = ImageComponentDecorator.DATA_KEY.getData(e.getDataContext());
        e.getPresentation().setEnabled(decorator != null && decorator.isEnabledForActionPlace(e.getPlace()));
        e.getPresentation().setText(isSelected(e) ? "Hide File Name" : "Show File Name");
    }
}
