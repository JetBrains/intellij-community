// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class InspectionProfileImporter implements SchemeImporter<InspectionProfileImpl> {
  @Override
  public String @NotNull [] getSourceExtensions() {
    return new String[]{"xml"};
  }

  @Override
  public @Nullable InspectionProfileImpl importScheme(@NotNull Project project,
                                                      @NotNull VirtualFile selectedFile,
                                                      @NotNull InspectionProfileImpl currentScheme,
                                                      @NotNull SchemeFactory<? extends InspectionProfileImpl> schemeFactory) {
    throw new UnsupportedOperationException();
  }
}
