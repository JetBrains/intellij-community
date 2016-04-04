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

import com.intellij.diff.tools.util.base.HighlightingLevel;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SetEditorSettingsAction extends ActionGroup implements DumbAware {
  @NotNull private final TextDiffSettingsHolder.TextDiffSettings myTextSettings;
  @NotNull private final List<? extends Editor> myEditors;

  @NotNull private final AnAction[] myActions;

  public SetEditorSettingsAction(@NotNull TextDiffSettingsHolder.TextDiffSettings settings,
                                 @NotNull List<? extends Editor> editors) {
    super("Editor Settings", null, AllIcons.General.SecondaryGroup);
    setPopup(true);
    myTextSettings = settings;
    myEditors = editors;

    for (Editor editor : myEditors) {
      ((EditorGutterComponentEx)editor.getGutter()).setGutterPopupGroup(this);
    }

    myActions = new AnAction[]{
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
        private boolean myForcedSoftWrap;

        @Override
        public boolean isSelected() {
          return myForcedSoftWrap || myTextSettings.isUseSoftWraps();
        }

        @Override
        public void setSelected(boolean state) {
          myForcedSoftWrap = false;
          myTextSettings.setUseSoftWraps(state);
        }

        @Override
        public void apply(@NotNull Editor editor, boolean value) {
          if (editor.getSettings().isUseSoftWraps() != value) {
            editor.getSettings().setUseSoftWraps(value);
          }
        }

        @Override
        public void applyDefaults(@NotNull List<? extends Editor> editors) {
          for (Editor editor : editors) {
            if (editor != null && editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS) != null) myForcedSoftWrap = true;
          }
          super.applyDefaults(editors);
        }
      },
      new EditorHighlightingLayerAction(),
    };
  }

  public void applyDefaults() {
    for (AnAction action : myActions) {
      ((EditorSettingAction)action).applyDefaults(myEditors);
    }
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    List<AnAction> result = new ArrayList<AnAction>();
    ContainerUtil.addAll(result, myActions);
    result.add(Separator.getInstance());
    result.add(ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_GUTTER_POPUP));
    return ContainerUtil.toArray(result, new AnAction[result.size()]);
  }

  private abstract class EditorSettingToggleAction extends ToggleAction implements DumbAware, EditorSettingAction {
    private EditorSettingToggleAction(@NotNull String actionId) {
      EmptyAction.setupAction(this, actionId, null);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return isSelected();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      setSelected(state);
      for (Editor editor : myEditors) {
        if (editor == null) continue;
        apply(editor, state);
      }
    }

    public abstract boolean isSelected();

    public abstract void setSelected(boolean value);

    public abstract void apply(@NotNull Editor editor, boolean value);

    public void applyDefaults(@NotNull List<? extends Editor> editors) {
      for (Editor editor : editors) {
        if (editor == null) continue;
        apply(editor, isSelected());
      }
    }
  }

  private class EditorHighlightingLayerAction extends ActionGroup implements EditorSettingAction {
    private final AnAction[] myOptions;

    public EditorHighlightingLayerAction() {
      super("Highlighting Level", true);
      myOptions = ContainerUtil.map(HighlightingLevel.values(), new Function<HighlightingLevel, AnAction>() {
        @Override
        public AnAction fun(HighlightingLevel level) {
          return new OptionAction(level);
        }
      }, AnAction.EMPTY_ARRAY);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return myOptions;
    }

    @Override
    public void applyDefaults(@NotNull List<? extends Editor> editors) {
      apply(myTextSettings.getHighlightingLevel());
    }

    private void apply(@NotNull HighlightingLevel layer) {
      for (Editor editor : myEditors) {
        if (editor == null) continue;
        ((EditorImpl)editor).setHighlightingFilter(layer.getCondition());
      }
    }

    private class OptionAction extends ToggleAction implements DumbAware {
      @NotNull private final HighlightingLevel myLayer;

      public OptionAction(@NotNull HighlightingLevel layer) {
        super(layer.getText(), null, layer.getIcon());
        myLayer = layer;
      }

      @Override
      public boolean isSelected(AnActionEvent e) {
        return myTextSettings.getHighlightingLevel() == myLayer;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myTextSettings.setHighlightingLevel(myLayer);
        apply(myLayer);
      }
    }
  }

  private interface EditorSettingAction {
    void applyDefaults(@NotNull List<? extends Editor> editors);
  }
}
