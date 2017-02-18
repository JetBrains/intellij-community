/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.compiler.ModuleSourceSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class GeneralProjectSettingsElement extends ProjectStructureElement {
  public GeneralProjectSettingsElement(@NotNull StructureConfigurableContext context) {
    super(context);
  }

  @Override
  public String getPresentableText() {
    return "Project";
  }

  @Override
  public String getPresentableName() {
    return ProjectStructureConfigurable.getInstance(myContext.getProject()).getProjectConfig().getProjectName();
  }

  @Override
  public String getTypeName() {
    return "Project";
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
    final Project project = myContext.getProject();
    if (containsModuleWithInheritedSdk()) {
      ProjectSdksModel model = ProjectStructureConfigurable.getInstance(project).getProjectJdksModel();
      Sdk sdk = model.getProjectSdk();
      if (sdk == null) {
        PlaceInProjectStructureBase place = new PlaceInProjectStructureBase(project, ProjectStructureConfigurable.getInstance(project).createProjectConfigurablePlace(), this);
        problemsHolder.registerProblem(ProjectBundle.message("project.roots.project.jdk.problem.message"), null,
                                       ProjectStructureProblemType.error("project-sdk-not-defined"), place,
                                       null);
      }
    }


    List<Chunk<ModuleSourceSet>> sourceSetCycles = ModuleCompilerUtil.computeSourceSetCycles(myContext.getModulesConfigurator());

    List<String> cycles = new ArrayList<>();

    for (Chunk<ModuleSourceSet> chunk : sourceSetCycles) {
      final Set<ModuleSourceSet> sourceSets = chunk.getNodes();
      List<String> names = new ArrayList<>();
      for (ModuleSourceSet sourceSet : sourceSets) {
        String name = sourceSet.getDisplayName();
        names.add(names.isEmpty() ? name : StringUtil.decapitalize(name));
      }
      cycles.add(StringUtil.join(names, ", "));
    }
    if (!cycles.isEmpty()) {
      final PlaceInProjectStructureBase place =
        new PlaceInProjectStructureBase(project, ProjectStructureConfigurable.getInstance(project).createModulesPlace(), this);
      final String message;
      final String description;
      if (cycles.size() > 1) {
        message = "Circular dependencies";
        @NonNls final String br = "<br>&nbsp;&nbsp;&nbsp;&nbsp;";
        StringBuilder cyclesString = new StringBuilder();
        for (int i = 0; i < cycles.size(); i++) {
          cyclesString.append(br).append(i + 1).append(". ").append(cycles.get(i));
        }
        description = ProjectBundle.message("module.circular.dependency.warning.description", cyclesString);
      }
      else {
        message = ProjectBundle.message("module.circular.dependency.warning.short", StringUtil.decapitalize(cycles.get(0)));
        description = null;
      }
      problemsHolder.registerProblem(new ProjectStructureProblemDescription(message, description, place,
                                                                            ProjectStructureProblemType
                                                                              .warning("module-circular-dependency"),
                                                                            Collections.<ConfigurationErrorQuickFix>emptyList()));
    }
  }

  private boolean containsModuleWithInheritedSdk() {
    for (Module module : myContext.getModules()) {
      ModuleRootModel rootModel = myContext.getModulesConfigurator().getRootModel(module);
      if (rootModel.isSdkInherited()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    return Collections.emptyList();
  }

  @Override
  public String getId() {
    return "project:general";
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof GeneralProjectSettingsElement;
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
