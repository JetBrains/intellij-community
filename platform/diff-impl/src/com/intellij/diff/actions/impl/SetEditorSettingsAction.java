// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions.impl;

import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.base.HighlightingLevel;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SetEditorSettingsAction extends ActionGroup implements DumbAware {
  @NotNull private final TextDiffSettings myTextSettings;
  @NotNull private final List<? extends Editor> myEditors;
  @Nullable private SyncScrollSupport.Support mySyncScrollSupport;

  @NotNull private final AnAction[] myActions;

  public SetEditorSettingsAction(@NotNull TextDiffSettings settings,
                                 @NotNull List<? extends Editor> editors) {
    super("Editor Settings", null, AllIcons.General.GearPlain);
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
          myTextSettings.setShowWhitespaces(state);
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
          if (editor.getSettings().isUseSoftWraps() == value) return;

          if (mySyncScrollSupport != null) mySyncScrollSupport.enterDisableScrollSection();
          try {
            AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editor, null, value);
          }
          finally {
            if (mySyncScrollSupport != null) mySyncScrollSupport.exitDisableScrollSection();
          }
        }

        @Override
        public void applyDefaults(@NotNull List<? extends Editor> editors) {
          if (!myTextSettings.isUseSoftWraps()) {
            for (Editor editor : editors) {
              myForcedSoftWrap = myForcedSoftWrap || ((EditorImpl)editor).shouldSoftWrapsBeForced();
            }
          }
          super.applyDefaults(editors);
        }
      },
      new EditorHighlightingLayerAction(),
    };
  }

  public void setSyncScrollSupport(@Nullable SyncScrollSupport.Support syncScrollSupport) {
    mySyncScrollSupport = syncScrollSupport;
  }

  public void applyDefaults() {
    for (AnAction action : myActions) {
      ((EditorSettingAction)action).applyDefaults(myEditors);
    }
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    AnAction editorSettingsGroup = ActionManager.getInstance().getAction("Diff.EditorGutterPopupMenu.EditorSettings");

    List<AnAction> actions = new ArrayList<>();
    ContainerUtil.addAll(actions, myActions);
    actions.add(editorSettingsGroup);
    actions.add(Separator.getInstance());

    if (e != null && ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    ActionGroup gutterGroup = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_GUTTER_POPUP);
    List<AnAction> result = ContainerUtil.newArrayList(gutterGroup.getChildren(e));
    result.add(Separator.getInstance());
    replaceOrAppend(result, editorSettingsGroup, new DefaultActionGroup(actions));
    return result.toArray(AnAction.EMPTY_ARRAY);
  }

  private static <T> void replaceOrAppend(List<T> list, T from, T to) {
    int index = list.indexOf(from);
    if (index == -1) index = list.size();
    list.remove(from);
    list.add(index, to);
  }

  private abstract class EditorSettingToggleAction extends ToggleAction implements DumbAware, EditorSettingAction {
    private EditorSettingToggleAction(@NotNull String actionId) {
      ActionUtil.copyFrom(this, actionId);
      getTemplatePresentation().setIcon(null);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return isSelected();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      setSelected(state);
      for (Editor editor : myEditors) {
        apply(editor, state);
      }
    }

    public abstract boolean isSelected();

    public abstract void setSelected(boolean value);

    public abstract void apply(@NotNull Editor editor, boolean value);

    public void applyDefaults(@NotNull List<? extends Editor> editors) {
      for (Editor editor : editors) {
        apply(editor, isSelected());
      }
    }
  }

  private class EditorHighlightingLayerAction extends ActionGroup implements EditorSettingAction, DumbAware {
    private final AnAction[] myOptions;

    public EditorHighlightingLayerAction() {
      super("Highlighting Level", true);
      myOptions = ContainerUtil.map(HighlightingLevel.values(), level -> new OptionAction(level), AnAction.EMPTY_ARRAY);
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
