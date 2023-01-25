// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolsSupplier;
import com.intellij.codeInspection.inspectionProfile.YamlInspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.parser.ParserException;

import java.io.File;

public abstract class InspectionProfileLoaderBase<T extends InspectionProfileImpl> implements InspectionProfileLoader<T> {
  protected Project project;

  public InspectionProfileLoaderBase(Project project) {
    this.project = project;
  }

  @Nullable
  protected InspectionProfileImpl tryLoadProfileFromYaml(@NotNull String profilePath,
                                                         @NotNull InspectionToolsSupplier inspectionToolsSupplier,
                                                         @NotNull BaseInspectionProfileManager profileManager) {
    if (!YamlInspectionProfileImpl.isYamlFile(profilePath)) {
      return null;
    }
    if (!new File(profilePath).isFile()) {
      throw new InspectionApplicationException("Inspection profile '" + profilePath + "' does not exist");
    }
    try {
      return YamlInspectionProfileImpl.loadFrom(project, profilePath, inspectionToolsSupplier, profileManager).buildEffectiveProfile();
    }
    catch (ParserException e) {
      // snakeyaml doesn't provide any information about where the YAML stream comes from,
      // its StreamReader constructor hardcodes the name to "'reader'".
      throw new InspectionApplicationException("Parse error in '" + profilePath + "': " + e);
    }
  }
}
