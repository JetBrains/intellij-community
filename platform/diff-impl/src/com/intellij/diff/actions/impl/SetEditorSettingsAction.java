/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.actions.impl;

import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class SetEditorSettingsAction extends ActionGroup implements DumbAware {
  @NotNull private final TextDiffSettingsHolder.TextDiffSettings myTextSettings;
  @NotNull private final List<? extends Editor> myEditors;

  @NotNull private final EditorSettingToggleAction[] myActions;

  public SetEditorSettingsAction(@NotNull TextDiffSettingsHolder.TextDiffSettings settings,
                                 @NotNull List<? extends Editor> editors) {
    super("Editor Settings", null, AllIcons.General.SecondaryGroup);
    setPopup(true);
    myTextSettings = settings;
    myEditors = editors;

    myActions = new EditorSettingToggleAction[]{
      new EditorSettingToggleAction("EditorToggleShowWhitespaces") {
        @Override
        public boolean isSelected() {
          return myTextSettings.isShowWhitespaces();
        }

        @Override
        public void setSelected(boolean state) {
          myTextSettings.setShowWhiteSpaces(state);
        }

        @Override
        public void apply(@NotNull Editor editor, boolean value) {
          if (editor.getSettings().isWhitespacesShown() != value) {
            editor.getSettings().setWhitespacesShown(value);
            editor.getComponent().repaint();
          }
        }
      },
      new EditorSettingToggleAction("EditorToggleShowLineNumbers") {
        @Override
        public boolean isSelected() {
          return myTextSettings.isShowLineNumbers();
        }

        @Override
        public void setSelected(boolean state) {
          myTextSettings.setShowLineNumbers(state);
        }

        @Override
        public void apply(@NotNull Editor editor, boolean value) {
          if (editor.getSettings().isLineNumbersShown() != value) {
            editor.getSettings().setLineNumbersShown(value);
            editor.getComponent().repaint();
          }
        }
      },
      new EditorSettingToggleAction("EditorToggleShowIndentLines") {
        @Override
        public boolean isSelected() {
          return myTextSettings.isShowIndentLines();
        }

        @Override
        public void setSelected(boolean state) {
          myTextSettings.setShowIndentLines(state);
        }

        @Override
        public void apply(@NotNull Editor editor, boolean value) {
          if (editor.getSettings().isIndentGuidesShown() != value) {
            editor.getSettings().setIndentGuidesShown(value);
            editor.getComponent().repaint();
          }
        }
      },
      new EditorSettingToggleAction("EditorToggleUseSoftWraps") {
        @Override
        public boolean isSelected() {
          return myTextSettings.isUseSoftWraps();
        }

        @Override
        public void setSelected(boolean state) {
          myTextSettings.setUseSoftWraps(state);
        }

        @Override
        public void apply(@NotNull Editor editor, boolean value) {
          if (editor.getSettings().isUseSoftWraps() != value) {
            editor.getSettings().setUseSoftWraps(value);
          }
        }
      },
    };
  }

  public void applyDefaults() {
    for (Editor editor : myEditors) {
      for (EditorSettingToggleAction action : myActions) {
        action.apply(editor, action.isSelected());
      }
    }
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myActions;
  }

  private abstract class EditorSettingToggleAction extends ToggleAction implements DumbAware {
    private EditorSettingToggleAction(@NotNull String actionId) {
      super(ActionsBundle.actionText(actionId), ActionsBundle.actionDescription(actionId), null);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return isSelected();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      for (Editor editor : myEditors) {
        setSelected(state);
        apply(editor, state);
      }
    }

    public abstract boolean isSelected();

    public abstract void setSelected(boolean value);

    public abstract void apply(@NotNull Editor editor, boolean value);
  }
}
