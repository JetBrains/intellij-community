/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.gotoByName.SimpleChooseByNameModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class GotoInspectionModel extends SimpleChooseByNameModel {
  private final Map<String, InspectionToolWrapper> myToolNames = new HashMap<>();
  private final String[] myNames;
  private final InspectionListCellRenderer myListCellRenderer = new InspectionListCellRenderer();


  public GotoInspectionModel(@NotNull Project project) {
    super(project, IdeBundle.message("prompt.goto.inspection.enter.name"), "goto.inspection.help.id");

    InspectionProfileImpl rootProfile = InspectionProfileManager.getInstance().getCurrentProfile();
    for (ScopeToolState state : rootProfile.getAllTools()) {
      InspectionToolWrapper tool = LocalInspectionToolWrapper.findTool2RunInBatch(project, null, rootProfile, state.getTool());
      if (tool != null) {
        String name = tool.getDisplayName() + " " + StringUtil.join(tool.getGroupPath(), " ");
        myToolNames.put(name, tool);
      }
    }
    myNames = ArrayUtil.toStringArray(myToolNames.keySet());
  }

  @Override
  public ListCellRenderer getListCellRenderer() {
    return myListCellRenderer;
  }

  @Override
  public String[] getNames() {
    return myNames;
  }

  @Override
  public Object[] getElementsByName(final String name, final String pattern) {
    final InspectionToolWrapper tool = myToolNames.get(name);
    if (tool == null) {
      return InspectionToolWrapper.EMPTY_ARRAY;
    }
    return new InspectionToolWrapper[] {tool};
  }

  @Override
  public String getElementName(final Object element) {
    if (element instanceof InspectionToolWrapper) {
      InspectionToolWrapper entry = (InspectionToolWrapper)element;
      return entry.getDisplayName() + " " + StringUtil.join(entry.getGroupPath(), " ");
    }
    return null;
  }
}
