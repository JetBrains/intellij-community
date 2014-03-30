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
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 09-Jan-2006
 */
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
      if (module.isDisposed()) return CommonProblemDescriptor.EMPTY_ARRAY;
      final Module[] declaredDependencies = ModuleRootManager.getInstance(module).getDependencies();
      List<CommonProblemDescriptor> descriptors = new ArrayList<CommonProblemDescriptor>();
      final Set<Module> modules = refModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
      for (final Module dependency : declaredDependencies) {
        if (modules == null || !modules.contains(dependency)) {
         final CommonProblemDescriptor problemDescriptor;
          if (scope.containsModule(dependency)) { //external references are rejected -> annotator doesn't provide any information on them -> false positives
            problemDescriptor = manager.createProblemDescriptor(
              InspectionsBundle.message("unnecessary.module.dependency.problem.descriptor", module.getName(), dependency.getName()),
              new RemoveModuleDependencyFix(module, dependency));
          } else {
            String message = InspectionsBundle
              .message("suspected.module.dependency.problem.descriptor", module.getName(), dependency.getName(), scope.getDisplayName(),
                       dependency.getName());
            problemDescriptor = manager.createProblemDescriptor(message);
          }
          descriptors.add(problemDescriptor);
        }
      }
      return descriptors.isEmpty() ? null : descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
    }
    return null;
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

  public static class RemoveModuleDependencyFix implements QuickFix {
    private final Module myModule;
    private final Module myDependency;

    public RemoveModuleDependencyFix(Module module, Module dependency) {
      myModule = module;
      myDependency = dependency;
    }

    @Override
    @NotNull
    public String getName() {
      return "Remove dependency";
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
      final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
      for (OrderEntry entry : model.getOrderEntries()) {
        if (entry instanceof ModuleOrderEntry) {
          final Module mDependency = ((ModuleOrderEntry)entry).getModule();
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
