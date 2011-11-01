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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
import com.intellij.util.Chunk;
import com.intellij.util.graph.Graph;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class GeneralProjectSettingsElement extends ProjectStructureElement {
  public GeneralProjectSettingsElement(@NotNull StructureConfigurableContext context) {
    super(context);
  }

  @Override
  public String getPresentableName() {
    return "Project";
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
    final Graph<Chunk<ModifiableRootModel>> graph = ModuleCompilerUtil.toChunkGraph(myContext.getModulesConfigurator().createGraphGenerator());
    final Collection<Chunk<ModifiableRootModel>> chunks = graph.getNodes();
    String cycles = "";
    int count = 0;
    for (Chunk<ModifiableRootModel> chunk : chunks) {
      final Set<ModifiableRootModel> modules = chunk.getNodes();
      String cycle = "";
      for (ModifiableRootModel model : modules) {
        cycle += ", " + model.getModule().getName();
      }
      if (modules.size() > 1) {
        @NonNls final String br = "<br>&nbsp;&nbsp;&nbsp;&nbsp;";
        cycles += br + (++count) + ". " + cycle.substring(2);
      }
    }
    if (count > 0) {
      @NonNls final String leftBrace = "<html>";
      @NonNls final String rightBrace = "</html>";
      final String fullDescription = leftBrace + ProjectBundle.message("module.circular.dependency.warning", cycles, count) + rightBrace;
      final Project project = myContext.getProject();
      for (Chunk<ModifiableRootModel> chunk : chunks) {
        final Set<ModifiableRootModel> nodes = chunk.getNodes();
        if (nodes.size() > 1) {
          final PlaceInProjectStructureBase place = new PlaceInProjectStructureBase(project, ProjectStructureConfigurable.getInstance(project).createModulesPlace(), this);
          StringBuilder names = new StringBuilder();
          for (ModifiableRootModel model : nodes) {
            if (names.length() > 0) names.append(", ");
            names.append(model.getModule().getName());
          }
          problemsHolder.registerProblem(new CircularDependencyProblemDescription("Circular dependency between modules " + names, fullDescription, place));
        }
      }
    }
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

  public static class CircularDependencyProblemDescription extends ProjectStructureProblemDescription {
    @NotNull private final String myFullDescription;

    public CircularDependencyProblemDescription(@NotNull String message,
                                                @NotNull String fullDescription,
                                                @NotNull PlaceInProjectStructure place) {
      super(message, null, place, ProjectStructureProblemType.warning("module-circular-dependency"),
            Collections.<ConfigurationErrorQuickFix>emptyList());
      myFullDescription = fullDescription;
    }

    @NotNull
    public String getFullDescription() {
      return myFullDescription;
    }
  }
}
