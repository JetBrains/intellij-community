/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class InspectionNode extends InspectionTreeNode {
  private final static Logger LOG = Logger.getInstance(InspectionNode.class);

  @NotNull private final InspectionProfileImpl myProfile;

  public InspectionNode(@NotNull InspectionToolWrapper toolWrapper, @NotNull InspectionProfileImpl profile) {
    super(toolWrapper);
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
    final String shortName = getToolWrapper().getShortName();
    final ToolsImpl tools = myProfile.getTools(shortName, null);
    LOG.assertTrue(tools != null, "Can't find tools for " + shortName);
    return tools.isEnabled() ? null : "Disabled";
  }
}
