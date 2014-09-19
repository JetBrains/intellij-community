/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.codeInspection.ex.Tools;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class InspectionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return "inspections";
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
    if (project == null) return ContainerUtil.emptyList();
    List<BooleanOptionDescription> result = ContainerUtil.newArrayList();
    List<Tools> tools = InspectionProjectProfileManager.getInstance(project).getInspectionProfile().getAllEnabledInspectionTools(project);
    for (Tools tool : tools) {
      result.add(new ToolOptionDescription(tool, project));
    }
    return result;
  }
}
