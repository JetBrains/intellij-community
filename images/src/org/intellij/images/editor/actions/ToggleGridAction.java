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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.actionSystem.ImageEditorActionUtil;

/**
 * Toggle grid lines over image.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#setGridVisible
 */
public final class ToggleGridAction extends ToggleAction implements DumbAware {
  public boolean isSelected(AnActionEvent e) {
    ImageEditor editor = ImageEditorActionUtil.getValidEditor(e);
    return editor != null && editor.isGridVisible();
  }

  public void setSelected(AnActionEvent e, boolean state) {
    ImageEditor editor = ImageEditorActionUtil.getValidEditor(e);
    if (editor != null) {
      editor.setGridVisible(state);
    }
  }

  public void update(final AnActionEvent e) {
    super.update(e);
    ImageEditorActionUtil.setEnabled(e);
    e.getPresentation().setText(isSelected(e) ? "Hide Grid" : "Show Grid");
  }
}
