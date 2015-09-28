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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.stubsHierarchy.impl.*;
import org.jetbrains.annotations.NotNull;

// Building hierarchy only for source files
public class TestStubHierarchyAction extends InheritanceAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubsHierarchy.TestStubHierarchyAction");
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      ProgressManager progress = ProgressManager.getInstance();
      progress.runProcessWithProgressSynchronously(new TestHierarchy(project), "Testing Hierarchy", true, project);
    }
  }

  private static class TestHierarchy implements Runnable {
    private final SingleClassHierarchy symbols;
    Project myProject;

    TestHierarchy(Project project) {
      myProject = project;
      symbols = HierarchyService.instance(myProject).getSingleClassHierarchy();
    }

    @Override
    public void run() {
      LOG.info("TestStubHierarchyAction started");
      final ProgressManager progressManager = ProgressManager.getInstance();
      final ProgressIndicator indicator = progressManager.getProgressIndicator();

      indicator.setText("Getting keys");
      final SmartClassAnchor[] classes = symbols.myClassAnchors;
      int size = symbols.myClassAnchors.length;
      for (int i = 0; i < size; i++) {
        final int finalI = i;
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            test(classes[finalI]);
          }
        });
        indicator.setFraction(i * 1.0 / (double)size);
      }
      LOG.info("TestStubHierarchyAction finished");
    }

    private void test(SmartClassAnchor aClass) {
      PsiClass psiClass = ClassAnchorUtil.retrieve(myProject, aClass);
      if (psiClass == null) {
        LOG.info("error testing: could not retrieve file for anchor: " + aClass);
        return;
      }

      test(aClass, psiClass, psiClass.getSuperClass());
      for (PsiClass inter : psiClass.getInterfaces()) {
        test(aClass, psiClass, inter);
      }
    }

    private void test(SmartClassAnchor anchor, PsiClass psiClass, PsiClass fromPsi) {
      if (fromPsi == null) {
        return;
      }
      String psiName = fromPsi.getQualifiedName();
      if ("java.lang.Object".equals(psiName) || "groovy.lang.GroovyObject".equals(psiName) ||  "groovy.lang.GroovyObjectSupport".equals(psiName)) {
        return;
      }
      // TODO - test using subtypes
      LOG.info("error testing " + classInfo(psiClass) + ": missing " + classInfo(fromPsi));

    }

    @NotNull
    private static String classInfo(PsiClass psiClass) {
      return psiClass + "[" + psiClass.getQualifiedName() + "]" + " (" + psiClass.getContainingFile().getVirtualFile().getPresentableUrl() + ")";
    }
  }

}
