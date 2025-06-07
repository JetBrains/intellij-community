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
package org.intellij.images.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import org.intellij.images.options.DefaultImageEditorSettings;
import org.intellij.images.ui.ImageComponentDecorator;
import org.jetbrains.annotations.NotNull;

/**
 * Show/hide background action.

 * @see ImageComponentDecorator#setTransparencyChessboardVisible
 */
final class ToggleTransparencyChessboardAction extends DumbAwareToggleAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    ImageComponentDecorator decorator = e.getData(ImageComponentDecorator.DATA_KEY);
    return decorator != null && decorator.isEnabledForActionPlace(e.getPlace()) && decorator.isTransparencyChessboardVisible();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    ImageComponentDecorator decorator = e.getData(ImageComponentDecorator.DATA_KEY);
    if (decorator != null && decorator.isEnabledForActionPlace(e.getPlace())) {
      decorator.setTransparencyChessboardVisible(state);
      DefaultImageEditorSettings.INSTANCE.setShowChessboard(state);
    }
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    ImageComponentDecorator decorator = e.getData(ImageComponentDecorator.DATA_KEY);
    e.getPresentation().setEnabled(decorator != null && decorator.isEnabledForActionPlace(e.getPlace()));
    e.getPresentation().setText(isSelected(e) ? IdeBundle.message("action.text.hide.chessboard") : IdeBundle.message("action.text.show.chessboard"));
  }
}
