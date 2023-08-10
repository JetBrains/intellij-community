/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.ImageEditorActionUtil;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.NotNull;

/**
 * Resize image to actual size.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#getZoomModel()
 * @see ImageZoomModel#setZoomFactor
 */
public final class ActualSizeAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ImageComponentDecorator decorator = ImageEditorActionUtil.getImageComponentDecorator(e);
        if (decorator != null) {
            ImageZoomModel zoomModel = decorator.getZoomModel();
            zoomModel.setZoomFactor(1.0d);
            zoomModel.setZoomLevelChanged(true);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        if (ImageEditorActionUtil.setEnabled(e)) {
            ImageComponentDecorator decorator = ImageEditorActionUtil.getImageComponentDecorator(e);
            ImageZoomModel zoomModel = decorator.getZoomModel();
            e.getPresentation().setEnabled(zoomModel.getZoomFactor() != 1.0d);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
}
