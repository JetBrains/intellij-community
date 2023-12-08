// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.profile.codeInspection.ui.InspectionMetaDataDialog;
import com.intellij.profile.codeInspection.ui.InspectionProfileActionProvider;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RegExpProfileActionProvider extends InspectionProfileActionProvider {
  @Override
  public @Nullable ActionGroup getAddActions(@NotNull SingleInspectionProfilePanel panel) {
    return getActionGroup(panel);
  }

  @Override
  public List<ActionToRegister> getActionsToRegister(SingleInspectionProfilePanel panel) {
    return List.of(new ActionToRegister(getActionGroup(panel), "regexp.profile.action.provider.add.group"));
  }

  @NotNull
  private static DefaultActionGroup getActionGroup(@NotNull SingleInspectionProfilePanel panel) {
    return new DefaultActionGroup(
      new AddCustomRegExpInspectionAction(panel, RegExpBundle.message("action.add.regexp.search.inspection.text"), false),
      new AddCustomRegExpInspectionAction(panel, RegExpBundle.message("action.add.regexp.replace.inspection.text"), true)
    );
  }

  static final class AddCustomRegExpInspectionAction extends DumbAwareAction {
    private final SingleInspectionProfilePanel myPanel;
    private final boolean myReplace;

    AddCustomRegExpInspectionAction(@NotNull SingleInspectionProfilePanel panel, @NlsActions.ActionText String text, boolean replace) {
      super(text);
      myPanel = panel;
      myReplace = replace;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final RegExpDialog dialog =
        new RegExpDialog(e.getProject(), true, myReplace ? RegExpInspectionConfiguration.InspectionPattern.EMPTY_REPLACE_PATTERN : null);
      if (!dialog.showAndGet()) return;

      final RegExpInspectionConfiguration.InspectionPattern pattern = dialog.getPattern();
      final InspectionProfileModifiableModel profile = myPanel.getProfile();
      final CustomRegExpInspection inspection = CustomRegExpInspection.getCustomRegExpInspection(profile);
      final Project project = e.getData(CommonDataKeys.PROJECT);
      if (project == null) return;
      final InspectionMetaDataDialog metaDataDialog = inspection.createMetaDataDialog(project, profile.getDisplayName(), null);
      if (pattern.replacement() != null) {
        metaDataDialog.showCleanupOption(false);
      }
      if (!metaDataDialog.showAndGet()) return;

      final RegExpInspectionConfiguration configuration = new RegExpInspectionConfiguration(metaDataDialog.getName());
      configuration.addPattern(pattern);
      configuration.setDescription(metaDataDialog.getDescription());
      configuration.setSuppressId(metaDataDialog.getSuppressId());
      configuration.setProblemDescriptor(metaDataDialog.getProblemDescriptor());
      configuration.setCleanup(metaDataDialog.isCleanup());

      configuration.setUuid(null);
      inspection.addConfiguration(configuration);
      CustomRegExpInspection.addInspectionToProfile(project, profile, configuration);
      profile.setModified(true);
      profile.getProfileManager().fireProfileChanged(profile);
      myPanel.selectInspectionTool(configuration.getUuid());
    }
  }

  @Override
  public boolean canDeleteInspection(InspectionProfileEntry entry) {
    return entry instanceof CustomRegExpInspection;
  }

  @Override
  public void deleteInspection(InspectionProfileEntry entry, String shortName) {
    if (entry instanceof CustomRegExpInspection regExpInspection) {
      regExpInspection.removeConfigurationWithUuid(shortName);
    }
  }
}
