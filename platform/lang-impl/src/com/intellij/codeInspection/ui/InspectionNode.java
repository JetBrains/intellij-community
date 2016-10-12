/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.ui;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class InspectionNode extends InspectionTreeNode {
  private final HighlightDisplayKey myKey;
  @NotNull private final InspectionProfile myProfile;

  public InspectionNode(@NotNull InspectionToolWrapper toolWrapper, @NotNull InspectionProfile profile) {
    super(toolWrapper);
    myKey = HighlightDisplayKey.find(toolWrapper.getShortName());
    myProfile = profile;
  }

  public String toString() {
    return getToolWrapper().getDisplayName();
  }

  @NotNull
  public InspectionToolWrapper getToolWrapper() {
    return (InspectionToolWrapper)getUserObject();
  }

  @Nullable
  @Override
  public String getCustomizedTailText() {
    return myProfile.isToolEnabled(myKey) ? null : "Disabled";
  }
}
