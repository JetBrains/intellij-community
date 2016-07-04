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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.stubsHierarchy.ClassHierarchy;
import com.intellij.psi.stubsHierarchy.HierarchyService;
import com.intellij.psi.stubsHierarchy.SmartClassAnchor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// Building hierarchy only for source files
public class TestStubHierarchyAction extends InheritanceAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubsHierarchy.TestStubHierarchyAction");
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ReadAction.run(new TestHierarchy(project)::run),
                                                                        "Testing Hierarchy", true, project);
    }
  }

  private static class TestHierarchy implements Runnable {
    private final Project myProject;

    TestHierarchy(Project project) {
      myProject = project;
    }

    @Override
    public void run() {
      LOG.info("TestStubHierarchyAction started");
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      HierarchyService service = HierarchyService.getService(myProject);
      service.clearHierarchy();

      indicator.setText("Building hierarchy");
      ClassHierarchy hierarchy = service.getHierarchy();
      MultiMap<SmartClassAnchor, SmartClassAnchor> supers = calcSupersMap(hierarchy);

      indicator.setText("Checking");
      compareSupers(indicator, supers, hierarchy);

      LOG.info("TestStubHierarchyAction finished");
    }

    private void compareSupers(ProgressIndicator indicator, MultiMap<SmartClassAnchor, SmartClassAnchor> supers, ClassHierarchy hierarchy) {
      List<VirtualFile> covered = getCoveredFiles(indicator, hierarchy);
      for (int i = 0; i < covered.size(); i++) {
        indicator.setFraction(i * 1.0 / covered.size());
        checkFile(supers, hierarchy, covered.get(i));
      }
    }

    private void checkFile(MultiMap<SmartClassAnchor, SmartClassAnchor> supers, ClassHierarchy hierarchy, VirtualFile vFile) {
      for (StubElement<?> element : getStubTree(vFile).getPlainListFromAllRoots()) {
        Object psi = element.getPsi();
        if (psi instanceof PsiClass && !(psi instanceof PsiTypeParameter)) {
          SmartClassAnchor anchor = hierarchy.findAnchor((PsiClass)psi);
          if (anchor == null) {
            throw new AssertionError("Class not indexed: " + psi + " in " + vFile);
          }
          compareSupers(anchor, supers.get(anchor));
        }
      }
    }

    @NotNull
    private List<VirtualFile> getCoveredFiles(ProgressIndicator indicator, ClassHierarchy hierarchy) {
      GlobalSearchScope allScope = GlobalSearchScope.allScope(myProject);
      GlobalSearchScope uncovered = hierarchy.restrictToUncovered(allScope);
      List<VirtualFile> covered = new ArrayList<>();
      FileBasedIndex.getInstance().iterateIndexableFiles(file -> {
        if (!file.isDirectory() && allScope.contains(file) && !uncovered.contains(file)) {
          covered.add(file);
        }
        return true;
      }, myProject, indicator);
      return covered;
    }

    @NotNull
    private StubTree getStubTree(VirtualFile vFile) {
      PsiFileWithStubSupport psiFile = (PsiFileWithStubSupport)PsiManager.getInstance(myProject).findFile(vFile);
      assert psiFile != null : "No PSI for " + vFile;
      StubTree stubTree = psiFile.getStubTree();
      return stubTree != null ? stubTree : ((PsiFileImpl)psiFile).calcStubTree();
    }

    private void compareSupers(final SmartClassAnchor anchor, final Collection<SmartClassAnchor> superAnchors) {
      PsiClass subClass = anchor.retrieveClass(myProject);
      List<PsiClass> stubSuperList = ContainerUtil.map(superAnchors, (anchor1) -> anchor1.retrieveClass(myProject));

      Set<PsiClass> psiSupers = new HashSet<>(ContainerUtil.filter(getPsiSupers(subClass), psiClass -> !isImplicit(psiClass.getQualifiedName())));
      Set<PsiClass> stubSupers = new HashSet<>(ContainerUtil.filter(stubSuperList, psiClass -> !isImplicit(psiClass.getQualifiedName())));

      if (!stubSupers.containsAll(psiSupers)) {
        psiSupers.removeAll(stubSupers);
        throw new AssertionError("Inconsistent hierarchy for " + classInfo(subClass) +
                  "\n  missing " + psiSupers.size() + ": " + StringUtil.join(psiSupers, TestHierarchy::classInfo, ", ")
        );
      }
    }

    @NotNull
    private static List<PsiClass> getPsiSupers(PsiClass subClass) {
      List<PsiClass> psiSupers = new ArrayList<>();
      PsiClass superClass = subClass.getSuperClass();
      ContainerUtil.addIfNotNull(psiSupers, superClass);
      Collections.addAll(psiSupers, subClass.getInterfaces());
      return psiSupers;
    }

    private static boolean isImplicit(@Nullable String qname) {
      return CommonClassNames.JAVA_LANG_OBJECT.equals(qname) ||
             "groovy.lang.GroovyObject".equals(qname) || "groovy.lang.GroovyObjectSupport".equals(qname);
    }

    @NotNull
    MultiMap<SmartClassAnchor, SmartClassAnchor> calcSupersMap(ClassHierarchy hierarchy) {
      MultiMap<SmartClassAnchor, SmartClassAnchor> supers = MultiMap.create();
      for (SmartClassAnchor aClass : hierarchy.getAllClasses()) {
        for (SmartClassAnchor subtype : hierarchy.getDirectSubtypeCandidates(aClass)) {
          supers.putValue(subtype, aClass);
        }
      }
      return supers;
    }

    @NotNull
    private static String classInfo(PsiClass psiClass) {
      String name = psiClass.getQualifiedName();
      if (name == null) name = psiClass.toString();
      return name + " (" + psiClass.getContainingFile().getVirtualFile().getPresentableUrl() + ")";
    }
  }

}
