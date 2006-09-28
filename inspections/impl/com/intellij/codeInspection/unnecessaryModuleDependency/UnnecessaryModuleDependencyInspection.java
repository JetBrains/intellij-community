/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection.unnecessaryModuleDependency;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
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

  public boolean isGraphNeeded() {
    return true;
  }

  public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, final GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefModule){
      final RefModule refModule = (RefModule)refEntity;
      final Module module = refModule.getModule();
      final Module[] declaredDependencies = ModuleRootManager.getInstance(module).getDependencies();
      List<CommonProblemDescriptor> descriptors = new ArrayList<CommonProblemDescriptor>();
      final Set<Module> modules = refModule.getUserData(UnnecessaryModuleDependencyAnnotator.DEPENDENCIES);
      for (Module dependency : declaredDependencies) {
        if (modules == null || !modules.contains(dependency)) {
          descriptors.add(manager.createProblemDescriptor(InspectionsBundle.message("unnecessary.module.dependency.problem.descriptor", module.getName(), dependency.getName())));
        }
      }
      return descriptors.isEmpty() ? null : descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
    }
    return null;
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
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
