// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InspectionNode extends InspectionTreeNode {
  private final @NotNull InspectionToolWrapper myToolWrapper;
  private final @NotNull InspectionProfileImpl myProfile;

  public InspectionNode(@NotNull InspectionToolWrapper toolWrapper,
                        @NotNull InspectionProfileImpl profile,
                        @NotNull InspectionTreeNode parent) {
    super(parent);
    myToolWrapper = toolWrapper;
    myProfile = profile;
  }

  public @NotNull InspectionToolWrapper getToolWrapper() {
    return myToolWrapper;
  }

  @Override
  public @Nullable String getTailText() {
    final String shortName = getToolWrapper().getShortName();
    return myProfile.getTools(shortName, null).isEnabled() ? null : InspectionsBundle.message("inspection.node.disabled.state");
  }

  @Override
  public String getPresentableText() {
    return getToolWrapper().getDisplayName();
  }
}
