package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;

/**
 * @author Dennis.Ushakov
 */
public class IdeaModifiableModelsProvider implements ModifiableModelsProvider {
  @Nullable
  public ModifiableRootModel getModuleModifiableModel(final Module module) {
    final Project project = module.getProject();
    StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(project).getContext();
    final ModuleEditor moduleEditor = context != null ? context.getModulesConfigurator().getModuleEditor(module) : null;
    return moduleEditor != null ? moduleEditor.getModifiableRootModelProxy() : ModuleRootManager.getInstance(module).getModifiableModel();
  }

  public void commitModuleModifiableModel(final ModifiableRootModel model) {
    if (!(model instanceof Proxy)) {
      model.commit();
    }
    //IDEA should commit this model instead of us, because it is was given from StructureConfigurableContext
  }

  public void disposeModuleModifiableModel(final ModifiableRootModel model) {
    if (!(model instanceof Proxy)) {
      model.dispose();
    }
    //IDEA should dispose this model instead of us, because it is was given from StructureConfigurableContext
  }

  public LibraryTable.ModifiableModel getLibraryTableModifiableModel() {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      if (!project.isInitialized()) {
        continue;
      }
      StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(project).getContext();
      LibraryTableModifiableModelProvider provider = context != null ? context.createModifiableModelProvider(LibraryTablesRegistrar.APPLICATION_LEVEL) : null;
      final LibraryTable.ModifiableModel modifiableModel = provider != null ? provider.getModifiableModel() : null;
      if (modifiableModel != null) {
        return modifiableModel;
      }
    }
    return LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
  }
}
