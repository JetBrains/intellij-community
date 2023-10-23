package org.intellij.lang.regexp.inspection.custom;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.profile.codeInspection.ui.InspectionTreeAdvertiser;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RegExpAdvertiser extends InspectionTreeAdvertiser {

  @Override
  public @NotNull List<AnAction> getActions(SingleInspectionProfilePanel panel) {
    return List.of(
      new RegExpProfileActionProvider.AddCustomRegExpInspectionAction(panel, RegExpBundle.message("inspection.tree.create.inspection"), false)
    );
  }

  @Override
  public List<CustomGroup> getCustomGroups() {
    return List.of(
      new CustomGroup(CustomRegExpFakeInspection.getGroup(), RegExpBundle.message("inspection.tree.group.description"))
    );
  }
}