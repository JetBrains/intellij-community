/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.stubsHierarchy.impl.test;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.stubsHierarchy.ClassHierarchy;
import com.intellij.psi.stubsHierarchy.HierarchyService;
import com.intellij.psi.stubsHierarchy.SmartClassAnchor;

import java.util.List;

public class BuildStubsHierarchyAction extends InheritanceAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubsHierarchy.impl.test.BuildStubsHierarchyAction");
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    HierarchyService service = HierarchyService.getService(project);
    service.clearHierarchy();

    long start = System.currentTimeMillis();
    ThrowableComputable<ClassHierarchy, RuntimeException> computable = () -> ReadAction.compute(service::getHierarchy);
    ClassHierarchy hierarchy = ProgressManager.getInstance().runProcessWithProgressSynchronously(computable, "Building Hierarchy", false, project);
    long elapsed = System.currentTimeMillis() - start;
    List<? extends SmartClassAnchor> covered = hierarchy.getCoveredClasses();
    LOG.info("Building stub hierarchy took " + elapsed + " ms" +
             "; classes=" + hierarchy.getAllClasses().size() +
             "; covered=" + covered.size() +
             "; ambiguous=" + covered.stream().filter(hierarchy::hasAmbiguousSupers).count());
  }
}
