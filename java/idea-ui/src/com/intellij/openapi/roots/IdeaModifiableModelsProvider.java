package com.intellij.openapi.roots;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;

/**
 * @author Dennis.Ushakov
 */
public class IdeaModifiableModelsProvider implements ModifiableModelsProvider {
  @Override
  @Nullable
  public ModifiableRootModel getModuleModifiableModel(final Module module) {
    final Project project = module.getProject();
    final ModulesConfigurator configurator = getModulesConfigurator(project);
    if (configurator != null) {
      if (!configurator.isModuleModelCommitted()) {
        final ModuleEditor moduleEditor = configurator.getModuleEditor(module);
        if (moduleEditor != null) {
          return moduleEditor.getModifiableRootModelProxy();
        }
      }
    }
    return ModuleRootManager.getInstance(module).getModifiableModel();
  }

  @Nullable
  private static ModulesConfigurator getModulesConfigurator(Project project) {
    StructureConfigurableContext context = getProjectStructureContext(project);
    return context != null ? context.getModulesConfigurator() : null;
  }

  @Override
  public void commitModuleModifiableModel(final ModifiableRootModel model) {
    if (!(model instanceof Proxy)) {
      model.commit();
    }
    //IDEA should commit this model instead of us, because it is was given from StructureConfigurableContext
  }

  @Override
  public void disposeModuleModifiableModel(final ModifiableRootModel model) {
    if (!(model instanceof Proxy)) {
      model.dispose();
    }
    //IDEA should dispose this model instead of us, because it is was given from StructureConfigurableContext
  }

  @Override
  public ModifiableFacetModel getFacetModifiableModel(Module module) {
    final ModulesConfigurator configurator = getModulesConfigurator(module.getProject());
    if (configurator != null) {
      return configurator.getFacetsConfigurator().getOrCreateModifiableModel(module);
    }
    return FacetManager.getInstance(module).createModifiableModel();
  }

  @Override
  public void commitFacetModifiableModel(Module module, ModifiableFacetModel model) {
    final ModulesConfigurator configurator = getModulesConfigurator(module.getProject());
    if (configurator == null || !(configurator.getFacetsConfigurator().getFacetModel(module) instanceof ModifiableFacetModel)) {
      model.commit();
    }
  }

  @Override
  public LibraryTable.ModifiableModel getLibraryTableModifiableModel() {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      if (!project.isInitialized()) {
        continue;
      }
      StructureConfigurableContext context = getProjectStructureContext(project);
      LibraryTableModifiableModelProvider provider = context != null ? context.createModifiableModelProvider(LibraryTablesRegistrar.APPLICATION_LEVEL) : null;
      final LibraryTable.ModifiableModel modifiableModel = provider != null ? provider.getModifiableModel() : null;
      if (modifiableModel != null) {
        return modifiableModel;
      }
    }
    return LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
  }

  @Override
  public LibraryTable.ModifiableModel getLibraryTableModifiableModel(Project project) {
    StructureConfigurableContext context = getProjectStructureContext(project);
    if (context != null) {
      LibraryTableModifiableModelProvider provider = context.createModifiableModelProvider(LibraryTablesRegistrar.PROJECT_LEVEL);
      return provider.getModifiableModel();
    }
    return LibraryTablesRegistrar.getInstance().getLibraryTable(project).getModifiableModel();
  }

  @Override
  public void disposeLibraryTableModifiableModel(LibraryTable.ModifiableModel model) {
    //IDEA should dispose this model instead of us, because it is was given from StructureConfigurableContext
    if (!(model instanceof LibrariesModifiableModel)) {
      Disposer.dispose(model);
    }
  }

  @Nullable
  private static StructureConfigurableContext getProjectStructureContext(Project project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return null;

    final ProjectStructureConfigurable structureConfigurable = ProjectStructureConfigurable.getInstance(project);
    return structureConfigurable.isUiInitialized() ? structureConfigurable.getContext() : null;
  }
}
