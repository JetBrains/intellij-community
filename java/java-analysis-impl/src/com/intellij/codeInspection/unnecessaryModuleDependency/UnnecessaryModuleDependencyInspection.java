package com.intellij.codeInspection.unnecessaryModuleDependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnnecessaryModuleDependencyInspection extends GlobalInspectionTool {
  @Override
  public RefGraphAnnotator getAnnotator(@NotNull final RefManager refManager) {
    return new UnnecessaryModuleDependencyAnnotator(refManager);
  }

  @Override
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity, @NotNull AnalysisScope scope, @NotNull InspectionManager manager, @NotNull final GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefModule){
      final RefModule refModule = (RefModule)refEntity;
      final Module module = refModule.getModule();
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      boolean onlyGeneratedSources = true;
      for (ContentEntry entry : moduleRootManager.getContentEntries()) {
        for (SourceFolder folder : entry.getSourceFolders()) {
          if (!JavaProjectRootsUtil.isForGeneratedSources(folder)) {
            onlyGeneratedSources = false;
            break;
          }
        }
      }
      if (onlyGeneratedSources) return null;

      final OrderEntry[] declaredDependencies = moduleRootManager.getOrderEntries();

      final List<CommonProblemDescriptor> descriptors = new ArrayList<>();
      final Set<Module> modules = refModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
      final List<Module> candidates = new ArrayList<>();
      for (final OrderEntry entry : declaredDependencies) {
        if (entry instanceof ModuleOrderEntry &&
            ((ModuleOrderEntry)entry).getScope() != DependencyScope.RUNTIME &&
            !((ModuleOrderEntry)entry).isExported()) {

          final Module dependency = ((ModuleOrderEntry)entry).getModule();
          if (dependency == null || modules != null && modules.remove(dependency)) {
            continue;
          }

          candidates.add(dependency);
        }
      }

      for (Module dependency : candidates) {
        if (modules != null) {
          HashSet<Module> outs = new HashSet<>();
          OrderEnumerator.orderEntries(dependency)
            .withoutSdk()
            .exportedOnly()
            .recursively()
            .forEachModule(outs::add);

          if (ContainerUtil.intersects(modules, outs)) continue;
        }

        descriptors.add(createDescriptor(scope, manager, module, dependency));
      }

      return descriptors.isEmpty() ? null : descriptors.toArray(CommonProblemDescriptor.EMPTY_ARRAY);
    }
    return null;
  }

  @Nullable
  @Override
  public QuickFix getQuickFix(String hint) {
    return new RemoveModuleDependencyFix(hint);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("unnecessary.module.dependency.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "UnnecessaryModuleDependencyInspection";
  }

  @Nullable
  @Override
  public String getHint(@NotNull QuickFix fix) {
    return fix instanceof RemoveModuleDependencyFix ? ((RemoveModuleDependencyFix)fix).myDependency : null;
  }

  private static CommonProblemDescriptor createDescriptor(AnalysisScope scope,
                                                          InspectionManager manager,
                                                          Module module,
                                                          Module dependency) {
    String dependencyName = dependency.getName();
    String moduleName = module.getName();
    if (scope.containsModule(dependency)) { //external references are rejected -> annotator doesn't provide any information on them -> false positives
      final String allContainsMessage = InspectionsBundle.message("unnecessary.module.dependency.problem.descriptor", moduleName, dependencyName);
      return manager.createProblemDescriptor(allContainsMessage, module, new RemoveModuleDependencyFix(dependencyName));
    }
    else {
      String message = InspectionsBundle.message("suspected.module.dependency.problem.descriptor", moduleName, dependencyName, scope.getDisplayName());
      return manager.createProblemDescriptor(message, module);
    }
  }

  public static class RemoveModuleDependencyFix implements QuickFix<ModuleProblemDescriptor> {

    private final String myDependency;

    public RemoveModuleDependencyFix(String dependency) {
      myDependency = dependency;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return "Remove dependency";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ModuleProblemDescriptor descriptor) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(descriptor.getModule()).getModifiableModel();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          final String mDependency = ((ModuleOrderEntry)entry).getModuleName();
          if (Comparing.equal(mDependency, myDependency)) {
            model.removeOrderEntry(entry);
            break;
          }
        }
      }
      model.commit();
    }
  }
}
