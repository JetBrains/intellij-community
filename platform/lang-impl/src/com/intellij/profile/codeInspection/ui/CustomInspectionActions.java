// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CustomInspectionActions {
  public static @Nullable DefaultActionGroup getAddActionGroup(SingleInspectionProfilePanel panel) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    InspectionProfileActionProvider.EP_NAME.getExtensionList().forEach(provider -> {
      final var group = provider.getAddActions(panel);
      if (group != null) actionGroup.add(group);
    });
    if (actionGroup.getChildrenCount() == 0) return null;
    actionGroup.setPopup(true);
    actionGroup.registerCustomShortcutSet(CommonShortcuts.getNew(), panel);
    final Presentation presentation = actionGroup.getTemplatePresentation();
    presentation.setIcon(AllIcons.General.Add);
    presentation.setText(InspectionsBundle.messagePointer("add.inspection.button"));
    return actionGroup;
  }

  public static final class RemoveInspectionAction extends DumbAwareAction {
    private final SingleInspectionProfilePanel myPanel;

    RemoveInspectionAction(@NotNull SingleInspectionProfilePanel panel) {
      super(InspectionsBundle.message("remove.inspection.button"), null, AllIcons.General.Remove);
      myPanel = panel;
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myPanel);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final InspectionToolWrapper<?, ?> selectedTool = myPanel.getSelectedTool();
      e.getPresentation().setEnabled(
        selectedTool != null &&
        selectedTool.getMainToolId() != null &&
        ContainerUtil.exists(InspectionProfileActionProvider.EP_NAME.getExtensionList(),
                             actionProvider -> actionProvider.canDeleteInspection(getInspection(myPanel.getProfile(),
                                                                                                selectedTool.getMainToolId())))
      );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final InspectionToolWrapper<?, ?> selectedTool = myPanel.getSelectedTool();
      final String shortName = selectedTool.getShortName();
      final String mainToolId = selectedTool.getMainToolId();
      myPanel.removeSelectedRow();
      final InspectionProfileModifiableModel profile = myPanel.getProfile();
      final InspectionProfileEntry inspection = getInspection(profile, mainToolId);
      InspectionProfileActionProvider.EP_NAME.getExtensionList().forEach(actionProvider -> {
        if (actionProvider.canDeleteInspection(inspection)) {
          actionProvider.deleteInspection(inspection, shortName);
        }
      });
      profile.removeTool(selectedTool);
      profile.setModified(true);
      fireProfileChanged(profile);
    }
  }

  public static InspectionProfileEntry getInspection(@NotNull InspectionProfile profile, @NonNls String shortName) {
    final InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool(shortName, (Project)null);
    assert wrapper != null;
    return wrapper.getTool();
  }

  public static void fireProfileChanged(@NotNull InspectionProfileImpl profile) {
    profile.getProfileManager().fireProfileChanged(profile);
  }
}
