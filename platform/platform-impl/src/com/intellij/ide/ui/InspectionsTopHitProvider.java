// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
final class InspectionsTopHitProvider implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  @NotNull
  @Override
  public String getId() {
    return "inspections";
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@NotNull Project project) {
    InspectionProfileImpl inspectionProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    List<OptionDescription> result = new ArrayList<>();
    for (InspectionToolWrapper<?, ?> toolWrapper : inspectionProfile.getInspectionTools(null)) {
      result.add(new ToolOptionDescription(toolWrapper, project));
    }
    return result;
  }
}
