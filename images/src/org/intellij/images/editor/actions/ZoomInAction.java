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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.ImageEditorActionUtil;

/**
 * Zoom in.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#getZoomModel
 */
public final class ZoomInAction extends AnAction implements DumbAware {
    public void actionPerformed(AnActionEvent e) {
        ImageEditor editor = ImageEditorActionUtil.getValidEditor(e);
        if (editor != null) {
            ImageZoomModel zoomModel = editor.getZoomModel();
            zoomModel.zoomIn();
        }
    }

    public void update(AnActionEvent e) {
        super.update(e);
        if (ImageEditorActionUtil.setEnabled(e)) {
            ImageEditor editor = ImageEditorActionUtil.getValidEditor(e);
            ImageZoomModel zoomModel = editor.getZoomModel();
            e.getPresentation().setEnabled(zoomModel.canZoomIn());
        }
    }
}
