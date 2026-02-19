package org.intellij.lang.regexp.inspection.custom;

import com.intellij.profile.codeInspection.ui.InspectionTreeAdvertiser;
import org.intellij.lang.regexp.RegExpBundle;

import java.util.List;

public class RegExpAdvertiser extends InspectionTreeAdvertiser {

  @Override
  public List<CustomGroup> getCustomGroups() {
    return List.of(
      new CustomGroup(CustomRegExpFakeInspection.getGroup(), RegExpBundle.message("inspection.tree.group.description"))
    );
  }
}