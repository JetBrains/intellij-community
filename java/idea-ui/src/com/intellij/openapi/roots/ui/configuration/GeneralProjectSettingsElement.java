// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.ModuleCompilerUtil;
import com.intellij.compiler.ModuleSourceSet;
import com.intellij.compiler.server.impl.BuildProcessCustomPluginsConfiguration;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Chunk;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GeneralProjectSettingsElement extends ProjectStructureElement {
  public GeneralProjectSettingsElement(@NotNull StructureConfigurableContext context) {
    super(context);
  }

  @Override
  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getPresentableText() {
    return IdeBundle.message("title.project");
  }

  @Override
  public String getPresentableName() {
    return myContext.getModulesConfigurator().getProjectStructureConfigurable().getProjectConfig().getProjectName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getTypeName() {
    return IdeBundle.message("title.project");
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
    final Project project = myContext.getProject();
    ProjectStructureConfigurable projectStructureConfigurable = myContext.getModulesConfigurator().getProjectStructureConfigurable();
    if (containsModuleWithInheritedSdk()) {
      ProjectSdksModel model = projectStructureConfigurable.getProjectJdksModel();
      Sdk sdk = model.getProjectSdk();
      if (sdk == null) {
        PlaceInProjectStructureBase place = new PlaceInProjectStructureBase(project, projectStructureConfigurable
          .createProjectConfigurablePlace(), this);
        problemsHolder.registerProblem(JavaUiBundle.message("project.roots.project.jdk.problem.message"), null,
                                       ProjectStructureProblemType.error("project-sdk-not-defined"), place,
                                       null);
      }
    }


    List<Chunk<ModuleSourceSet>> sourceSetCycles = ModuleCompilerUtil.computeSourceSetCycles(myContext.getModulesConfigurator());

    List<@Nls String> cycles = new ArrayList<>();

    for (Chunk<ModuleSourceSet> chunk : sourceSetCycles) {
      final Set<ModuleSourceSet> sourceSets = chunk.getNodes();
      List<@Nls String> names = new ArrayList<>();
      for (ModuleSourceSet sourceSet : sourceSets) {
        String name = sourceSet.getDisplayName();
        names.add(names.isEmpty() ? name : StringUtil.decapitalize(name));
      }
      cycles.add(StringUtil.join(names, ", "));
    }
    if (!cycles.isEmpty()) {
      final PlaceInProjectStructureBase place =
        new PlaceInProjectStructureBase(project, projectStructureConfigurable.createModulesPlace(), this);
      final String message;
      final HtmlChunk description;
      if (cycles.size() > 1) {
        message = JavaUiBundle.message("circular.dependencies.message");

        final String header = JavaUiBundle.message("module.circular.dependency.warning.description");

        final HtmlChunk[] liTags = cycles.stream()
          .map(c -> HtmlChunk.tag("li").addText(c))
          .toArray(HtmlChunk[]::new);

        final HtmlChunk.Element ol = HtmlChunk.tag("ol").style("padding-left: 30pt;")
          .children(liTags);

        description = new HtmlBuilder()
          .append(HtmlChunk.tag("b").addText(header))
          .append(ol)
          .toFragment()
        ;
      }
      else {
        message = JavaUiBundle.message("module.circular.dependency.warning.short", StringUtil.decapitalize(cycles.get(0)));
        description = HtmlChunk.empty();
      }
      problemsHolder.registerProblem(new ProjectStructureProblemDescription(message, description, place,
                                                                            ProjectStructureProblemType
                                                                              .warning("module-circular-dependency"),
                                                                            Collections.emptyList()));
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
    List<ProjectStructureElementUsage> usages = new ArrayList<>();

    Collection<UnloadedModuleDescription> unloadedModules = ModuleManager.getInstance(myContext.getProject()).getUnloadedModuleDescriptions();
    if (!unloadedModules.isEmpty()) {
      MultiMap<Module, UnloadedModuleDescription> dependenciesInUnloadedModules = new MultiMap<>();
      for (UnloadedModuleDescription unloaded : unloadedModules) {
        for (String moduleName : unloaded.getDependencyModuleNames()) {
          Module depModule = myContext.getModulesConfigurator().getModuleModel().findModuleByName(moduleName);
          if (depModule != null) {
            dependenciesInUnloadedModules.putValue(depModule, unloaded);
          }
        }
      }

      for (Map.Entry<Module, Collection<UnloadedModuleDescription>> entry : dependenciesInUnloadedModules.entrySet()) {
        usages.add(new UsagesInUnloadedModules(myContext, this, new ModuleProjectStructureElement(myContext, entry.getKey()),
                                               entry.getValue()));
      }

      //currently we don't store dependencies on project libraries from unloaded modules in the model, so suppose that all the project libraries are used
      for (Library library : myContext.getProjectLibrariesProvider().getModifiableModel().getLibraries()) {
        usages.add(new UsagesInUnloadedModules(myContext, this, new LibraryProjectStructureElement(myContext, library), unloadedModules));
      }
    }

    for (String libraryName : BuildProcessCustomPluginsConfiguration.getInstance(myContext.getProject()).getProjectLibraries()) {
      Library library = myContext.getProjectLibrariesProvider().getModifiableModel().getLibraryByName(libraryName);
      if (library != null) {
        usages.add(new UsageInProjectSettings(myContext, new LibraryProjectStructureElement(myContext, library),
                                              JavaUiBundle.message("label.build.process.configuration")));
      }
    }

    return usages;
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
