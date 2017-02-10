package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ModuleProjectStructureElement extends ProjectStructureElement {
  private final Module myModule;

  public ModuleProjectStructureElement(@NotNull StructureConfigurableContext context, @NotNull Module module) {
    super(context);
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  public void checkModulesNames(ProjectStructureProblemsHolder problemsHolder) {
    final ModifiableModuleModel moduleModel = myContext.getModulesConfigurator().getModuleModel();
    final Module[] all = moduleModel.getModules();
    if (!ArrayUtil.contains(myModule, all)) {
      return;//module has been deleted
    }

    for (Module each : all) {
      if (each != myModule && myContext.getRealName(each).equals(myContext.getRealName(myModule))) {
        problemsHolder.registerProblem(ProjectBundle.message("project.roots.module.duplicate.name.message"), null,
                                       ProjectStructureProblemType.error("duplicate-module-name"), createPlace(),
                                       null);
        break;
      }
    }
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
    checkModulesNames(problemsHolder);

    final ModuleRootModel rootModel = myContext.getModulesConfigurator().getRootModel(myModule);
    if (rootModel == null) return; //already disposed
    final OrderEntry[] entries = rootModel.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (!entry.isValid()){
        if (entry instanceof JdkOrderEntry && ((JdkOrderEntry)entry).getJdkName() == null) {
          if (!(entry instanceof InheritedJdkOrderEntry)) {
            problemsHolder.registerProblem(ProjectBundle.message("project.roots.module.jdk.problem.message"), null, ProjectStructureProblemType.error("module-sdk-not-defined"), createPlace(entry),
                                           null);
          }
        }
        else {
          problemsHolder.registerProblem(ProjectBundle.message("project.roots.library.problem.message", entry.getPresentableName()), null,
                                         ProjectStructureProblemType.error("invalid-module-dependency"), createPlace(entry),
                                         null);
        }
      }
      //todo[nik] highlight libraries with invalid paths in ClasspathEditor
      //else if (entry instanceof LibraryOrderEntry) {
      //  final LibraryEx library = (LibraryEx)((LibraryOrderEntry)entry).getLibrary();
      //  if (library != null) {
      //    if (!library.allPathsValid(OrderRootType.CLASSES)) {
      //      problemsHolder.registerError(ProjectBundle.message("project.roots.tooltip.library.misconfigured", entry.getName()));
      //    }
      //    else if (!library.allPathsValid(OrderRootType.SOURCES)) {
      //      problemsHolder.registerWarning(ProjectBundle.message("project.roots.tooltip.library.misconfigured", entry.getName()));
      //    }
      //  }
      //}
    }
  }

  private PlaceInProjectStructure createPlace() {
    final Project project = myContext.getProject();
    return new PlaceInProjectStructureBase(project, ProjectStructureConfigurable.getInstance(project).createModulePlace(myModule), this);
  }

  private PlaceInProjectStructure createPlace(OrderEntry entry) {
    return new PlaceInModuleClasspath(myContext, myModule, this, entry);
  }

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    final List<ProjectStructureElementUsage> usages = new ArrayList<>();
    final ModuleEditor moduleEditor = myContext.getModulesConfigurator().getModuleEditor(myModule);
    if (moduleEditor != null) {
      for (OrderEntry entry : moduleEditor.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
          final Module module = moduleOrderEntry.getModule();
          if (module != null) {
            usages.add(new UsageInModuleClasspath(myContext, this, new ModuleProjectStructureElement(myContext, module), moduleOrderEntry.getScope()));
          }
        }
        else if (entry instanceof LibraryOrderEntry) {
          LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
          final Library library = libraryOrderEntry.getLibrary();
          if (library != null) {
            usages.add(new UsageInModuleClasspath(myContext, this, new LibraryProjectStructureElement(myContext, library),
                                                  libraryOrderEntry.getScope()));
          }
        }
        else if (entry instanceof JdkOrderEntry) {
          final Sdk jdk = ((JdkOrderEntry)entry).getJdk();
          if (jdk != null) {
            usages.add(new UsageInModuleClasspath(myContext, this, new SdkProjectStructureElement(myContext, jdk), null));
          }
        }
      }
    }
    return usages;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleProjectStructureElement)) return false;

    return myModule.equals(((ModuleProjectStructureElement)o).myModule);

  }

  @Override
  public int hashCode() {
    return myModule.hashCode();
  }

  @Override
  public String getPresentableName() {
    return myModule.getName();
  }

  @Override
  public String getTypeName() {
    return "Module";
  }

  @Override
  public String getId() {
    return "module:" + myModule.getName();
  }
}
