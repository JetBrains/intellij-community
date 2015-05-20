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
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Chunk;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.*;

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


    Graph<ModuleSourceSet> graph = ModuleCompilerUtil.createModuleSourceDependenciesGraph(myContext.getModulesConfigurator());
    Collection<Chunk<ModuleSourceSet>> chunks = GraphAlgorithms.getInstance().computeStronglyConnectedComponents(graph);
    List<Chunk<ModuleSourceSet>> sourceSetCycles =
      removeSingleElementChunks(removeDummyNodes(filterDuplicates(removeSingleElementChunks(chunks))));

    List<String> cycles = new ArrayList<String>();

    for (Chunk<ModuleSourceSet> chunk : sourceSetCycles) {
      final Set<ModuleSourceSet> sourceSets = chunk.getNodes();
      List<String> names = new ArrayList<String>();
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

  private List<Chunk<ModuleSourceSet>> removeDummyNodes(List<Chunk<ModuleSourceSet>> chunks) {
    List<Chunk<ModuleSourceSet>> result = new ArrayList<Chunk<ModuleSourceSet>>(chunks.size());
    for (Chunk<ModuleSourceSet> chunk : chunks) {
      Set<ModuleSourceSet> nodes = new LinkedHashSet<ModuleSourceSet>();
      for (ModuleSourceSet sourceSet : chunk.getNodes()) {
        if (!isDummy(sourceSet)) {
          nodes.add(sourceSet);
        }
      }
      result.add(new Chunk<ModuleSourceSet>(nodes));
    }
    return result;
  }

  private boolean isDummy(ModuleSourceSet set) {
    JavaSourceRootType type = set.getType() == ModuleSourceSet.Type.PRODUCTION ? JavaSourceRootType.SOURCE : JavaSourceRootType.TEST_SOURCE;
    ModuleRootModel rootModel = myContext.getModulesConfigurator().getRootModel(set.getModule());
    for (ContentEntry entry : rootModel.getContentEntries()) {
      if (!entry.getSourceFolders(type).isEmpty()) {
        return false;
      }
    }
    return true;
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

  private static List<Chunk<ModuleSourceSet>> removeSingleElementChunks(Collection<Chunk<ModuleSourceSet>> chunks) {
    return ContainerUtil.filter(chunks, new Condition<Chunk<ModuleSourceSet>>() {
      @Override
      public boolean value(Chunk<ModuleSourceSet> chunk) {
        return chunk.getNodes().size() > 1;
      }
    });
  }

  /**
   * Remove cycles in tests included in cycles between production parts
   */
  @NotNull
  private static List<Chunk<ModuleSourceSet>> filterDuplicates(@NotNull Collection<Chunk<ModuleSourceSet>> sourceSetCycles) {
    final List<Set<Module>> productionCycles = new ArrayList<Set<Module>>();

    for (Chunk<ModuleSourceSet> cycle : sourceSetCycles) {
      ModuleSourceSet.Type type = getCommonType(cycle);
      if (type == ModuleSourceSet.Type.PRODUCTION) {
        productionCycles.add(ModuleSourceSet.getModules(cycle.getNodes()));
      }
    }

    return ContainerUtil.filter(sourceSetCycles, new Condition<Chunk<ModuleSourceSet>>() {
      @Override
      public boolean value(Chunk<ModuleSourceSet> chunk) {
        if (getCommonType(chunk) != ModuleSourceSet.Type.TEST) return true;
        for (Set<Module> productionCycle : productionCycles) {
          if (productionCycle.containsAll(ModuleSourceSet.getModules(chunk.getNodes()))) return false;
        }
        return true;
      }
    });
  }

  @Nullable
  private static ModuleSourceSet.Type getCommonType(@NotNull Chunk<ModuleSourceSet> cycle) {
    ModuleSourceSet.Type type = null;
    for (ModuleSourceSet set : cycle.getNodes()) {
      if (type == null) {
        type = set.getType();
      }
      else if (type != set.getType()) {
        return null;
      }
    }
    return type;
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
