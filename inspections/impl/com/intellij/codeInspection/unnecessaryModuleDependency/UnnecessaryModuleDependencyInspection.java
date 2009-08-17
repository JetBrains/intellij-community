/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
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

  public RefGraphAnnotator getAnnotator(final RefManager refManager) {
    return new UnnecessaryModuleDependencyAnnotator(refManager);
  }

  public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, final GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefModule){
      final RefModule refModule = (RefModule)refEntity;
      final Module module = refModule.getModule();
      final Module[] declaredDependencies = ModuleRootManager.getInstance(module).getDependencies();
      List<CommonProblemDescriptor> descriptors = new ArrayList<CommonProblemDescriptor>();
      final Set<Module> modules = refModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
      for (final Module dependency : declaredDependencies) {
        if (scope.contains(dependency.getModuleFile())) { //external references are rejected -> annotator doesn't provide any information on them -> false positives
          if (modules == null || !modules.contains(dependency)) {
            descriptors.add(manager.createProblemDescriptor(InspectionsBundle.message("unnecessary.module.dependency.problem.descriptor", module.getName(), dependency.getName()), new QuickFix(){
              @NotNull
              public String getName() {
                return "Remove dependency";
              }

              @NotNull
              public String getFamilyName() {
                return getName();
              }

              public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
                final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
                for (OrderEntry entry : model.getOrderEntries()) {
                  if (entry instanceof ModuleOrderEntry) {
                    final Module mDependency = ((ModuleOrderEntry)entry).getModule();
                    if (Comparing.equal(mDependency, dependency)) {
                      model.removeOrderEntry(entry);
                      break;
                    }
                  }
                }
                model.commit();
              }
            }));
          }
        }
      }
      return descriptors.isEmpty() ? null : descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
    }
    return null;
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("unnecessary.module.dependency.display.name");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "UnnecessaryModuleDependencyInspection";
  }
}
