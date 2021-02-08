package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.CommonBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
        problemsHolder.registerProblem(JavaUiBundle.message("project.roots.module.duplicate.name.message"), null,
                                       ProjectStructureProblemType.error("duplicate-module-name"), createPlace(),
                                       null);
        break;
      }
    }
  }

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
    checkModulesNames(problemsHolder);

    final ModuleRootModel rootModel = getRootModel();
    if (rootModel == null) return; //already disposed
    final OrderEntry[] entries = rootModel.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (!entry.isValid()){
        if (entry instanceof JdkOrderEntry && ((JdkOrderEntry)entry).getJdkName() == null) {
          if (!(entry instanceof InheritedJdkOrderEntry)) {
            problemsHolder.registerProblem(JavaUiBundle.message("project.roots.module.jdk.problem.message"), null, ProjectStructureProblemType.error("module-sdk-not-defined"), createPlace(entry),
                                           null);
          }
        }
        else {
          problemsHolder.registerProblem(JavaUiBundle.message("project.roots.library.problem.message", entry.getPresentableName()), null,
                                         ProjectStructureProblemType.error("invalid-module-dependency"), createPlace(entry),
                                         null);
        }
      }
    }
  }

  private ModuleRootModel getRootModel() {
    ModuleEditor moduleEditor = myContext.getModulesConfigurator().getModuleEditor(myModule);
    if (moduleEditor == null) return ModuleRootManager.getInstance(myModule);
    return moduleEditor.getRootModel();
  }

  private PlaceInProjectStructure createPlace() {
    final Project project = myContext.getProject();
    Place place = myContext.getModulesConfigurator().getProjectStructureConfigurable().createModulePlace(myModule);
    return new PlaceInProjectStructureBase(project, place, this);
  }

  private PlaceInProjectStructure createPlace(OrderEntry entry) {
    return new PlaceInModuleClasspath(myContext, myModule, this, entry);
  }

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    final List<ProjectStructureElementUsage> usages = new ArrayList<>();
    final ModuleRootModel rootModel = getRootModel();
    if (rootModel != null) {
      for (OrderEntry entry : rootModel.getOrderEntries()) {
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
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getTypeName() {
    return CommonBundle.message("label.module");
  }

  @Override
  public String getId() {
    return "module:" + myModule.getName();
  }
}
