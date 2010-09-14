package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
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

  @Override
  public void check(ProjectStructureProblemsHolder problemsHolder) {
    final ModifiableModuleModel moduleModel = myContext.getModulesConfigurator().getModuleModel();
    final Module[] all = moduleModel.getModules();
    if (!ArrayUtil.contains(myModule, all)) {
      return;//module has been deleted
    }

    for (Module each : all) {
      if (each != myModule && myContext.getRealName(each).equals(myContext.getRealName(myModule))) {
        problemsHolder.registerError(ProjectBundle.message("project.roots.module.duplicate.name.message"));
        break;
      }
    }

    final ModuleRootModel rootModel = myContext.getModulesConfigurator().getRootModel(myModule);
    if (rootModel == null) return; //already disposed
    final OrderEntry[] entries = rootModel.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (!entry.isValid()){
        if (entry instanceof JdkOrderEntry && ((JdkOrderEntry)entry).getJdkName() == null) {
          problemsHolder.registerError(ProjectBundle.message("project.roots.module.jdk.problem.message"));
        } else {
          problemsHolder.registerError(ProjectBundle.message("project.roots.library.problem.message", entry.getPresentableName()));
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

  @Override
  public List<ProjectStructureElementUsage> getUsagesInElement() {
    final List<ProjectStructureElementUsage> usages = new ArrayList<ProjectStructureElementUsage>();
    final ModuleEditor moduleEditor = myContext.getModulesConfigurator().getModuleEditor(myModule);
    if (moduleEditor != null) {
      for (OrderEntry entry : moduleEditor.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          final Module module = ((ModuleOrderEntry)entry).getModule();
          if (module != null) {
            usages.add(new UsageInModuleClasspath(myContext, this, new ModuleProjectStructureElement(myContext, module)));
          }
        }
        else if (entry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (library != null) {
            usages.add(new UsageInModuleClasspath(myContext, this, new LibraryProjectStructureElement(myContext, library)));
          }
        }
        else if (entry instanceof JdkOrderEntry) {
          final Sdk jdk = ((JdkOrderEntry)entry).getJdk();
          if (jdk != null) {
            usages.add(new UsageInModuleClasspath(myContext, this, new SdkProjectStructureElement(myContext, jdk)));
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
  public String toString() {
    return "module:" + myModule.getName();
  }

  @Override
  public boolean highlightIfUnused() {
    return false;
  }
}
