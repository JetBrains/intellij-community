// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl;

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.base.HighlightingLevel;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.breadcrumbs.BreadcrumbsPlacement;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class SetEditorSettingsActionGroup extends ActionGroup implements DumbAware {
  @NotNull private final TextDiffSettings myTextSettings;
  private final @NotNull Supplier<? extends List<? extends Editor>> myEditors;
  @Nullable private SyncScrollSupport.Support mySyncScrollSupport;

  protected final AnAction @NotNull [] myActions;

  @ApiStatus.Internal
  public SetEditorSettingsActionGroup(@NotNull TextDiffSettings settings,
                                      @NotNull List<? extends Editor> editors) {
    this(settings, () -> editors);
  }

  @ApiStatus.Internal
  public SetEditorSettingsActionGroup(@NotNull TextDiffSettings settings,
                                      @NotNull Supplier<? extends List<? extends Editor>> editors) {
    super(DiffBundle.message("editor.settings"), null, AllIcons.General.GearPlain);
    myTextSettings = settings;
    myEditors = editors;

    installGutterPopup();

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
          boolean hasForcedSoftWraps = ContainerUtil.exists(myEditors.get(), editor -> {
            return Boolean.TRUE.equals(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS));
          });
          return myForcedSoftWrap || myTextSettings.isUseSoftWraps() || hasForcedSoftWraps;
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
              myForcedSoftWrap = myForcedSoftWrap || ((EditorImpl)editor).getSoftWrapModel().shouldSoftWrapsBeForced();
            }
          }
          super.applyDefaults(editors);
        }
      },
      new EditorHighlightingLayerGroup(),
      new EditorBreadcrumbsPlacementGroup(),
    };
  }

  public void setSyncScrollSupport(@Nullable SyncScrollSupport.Support syncScrollSupport) {
    mySyncScrollSupport = syncScrollSupport;
  }

  public void installGutterPopup() {
    for (Editor editor : myEditors.get()) {
      ((EditorGutterComponentEx)editor.getGutter()).setGutterPopupGroup(this);
    }
  }

  public void applyDefaults() {
    for (AnAction action : myActions) {
      if (action instanceof EditorSettingAction) {
        ((EditorSettingAction)action).applyDefaults(myEditors.get());
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setPopupGroup(e.isFromActionToolbar());
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    AnAction editorSettingsGroup = ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_SETTINGS);

    List<AnAction> actions = new ArrayList<>();
    ContainerUtil.addAll(actions, myActions);
    actions.add(editorSettingsGroup);
    actions.add(Separator.getInstance());

    if (e != null && e.getData(DiffDataKeys.MERGE_VIEWER) != null) {
      actions.add(Separator.getInstance());
      actions.add(ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));
    }

    if (e != null && ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }

    ActionGroup gutterGroup = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_GUTTER_POPUP);
    List<AnAction> result = new ArrayList<>(Arrays.asList(gutterGroup.getChildren(e)));
    result.add(Separator.getInstance());
    replaceOrAppend(result, editorSettingsGroup, new DefaultActionGroup(actions));
    return result.toArray(AnAction.EMPTY_ARRAY);
  }

  protected static <T> void replaceOrAppend(List<T> list, T from, T to) {
    int index = list.indexOf(from);
    if (index == -1) index = list.size();
    list.remove(from);
    list.add(index, to);
  }

  private abstract class EditorSettingToggleAction extends ToggleAction implements DumbAware, EditorSettingAction {
    private EditorSettingToggleAction(@NotNull @NonNls String actionId) {
      ActionUtil.copyFrom(this, actionId);
      getTemplatePresentation().setIcon(null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return isSelected();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      setSelected(state);
      for (Editor editor : myEditors.get()) {
        apply(editor, state);
      }
    }

    public abstract boolean isSelected();

    public abstract void setSelected(boolean value);

    public abstract void apply(@NotNull Editor editor, boolean value);

    @Override
    public void applyDefaults(@NotNull List<? extends Editor> editors) {
      for (Editor editor : editors) {
        apply(editor, isSelected());
      }
    }
  }

  private class EditorHighlightingLayerGroup extends ActionGroup implements EditorSettingAction, DumbAware {
    private final AnAction[] myOptions;

    EditorHighlightingLayerGroup() {
      super(DiffBundle.message("highlighting.level"), true);
      myOptions = ContainerUtil.map(HighlightingLevel.values(), level -> new OptionAction(level), AnAction.EMPTY_ARRAY);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return myOptions;
    }

    @Override
    public void applyDefaults(@NotNull List<? extends Editor> editors) {
      apply(myTextSettings.getHighlightingLevel());
    }

    private void apply(@NotNull HighlightingLevel layer) {
      for (Editor editor : myEditors.get()) {
        if (editor instanceof EditorImpl) {
          ((EditorImpl)editor).setHighlightingPredicate(layer.getCondition());
        }
      }
    }

    private class OptionAction extends ToggleAction implements DumbAware {
      @NotNull private final HighlightingLevel myLayer;

      OptionAction(@NotNull HighlightingLevel layer) {
        super(layer.getText(), null, layer.getIcon());
        myLayer = layer;
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return myTextSettings.getHighlightingLevel() == myLayer;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        myTextSettings.setHighlightingLevel(myLayer);
        apply(myLayer);
      }
    }
  }

  private class EditorBreadcrumbsPlacementGroup extends ActionGroup implements EditorSettingAction, DumbAware {
    private final AnAction[] myOptions;

    EditorBreadcrumbsPlacementGroup() {
      ActionUtil.copyFrom(this, IdeActions.BREADCRUMBS_OPTIONS_GROUP);
      myOptions = ContainerUtil.map(BreadcrumbsPlacement.values(), option -> new OptionAction(option), AnAction.EMPTY_ARRAY);
      setPopup(true);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return myOptions;
    }

    @Override
    public void applyDefaults(@NotNull List<? extends Editor> editors) {
    }

    private class OptionAction extends ToggleAction implements DumbAware {
      @NotNull private final BreadcrumbsPlacement myOption;

      OptionAction(@NotNull BreadcrumbsPlacement option) {
        ActionUtil.copyFrom(this, option.getActionId());
        myOption = option;
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return myTextSettings.getBreadcrumbsPlacement() == myOption;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        myTextSettings.setBreadcrumbsPlacement(myOption);
      }
    }
  }

  private interface EditorSettingAction {
    void applyDefaults(@NotNull List<? extends Editor> editors);
  }
}
