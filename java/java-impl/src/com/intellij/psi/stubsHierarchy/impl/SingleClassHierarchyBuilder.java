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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Set;

public class SingleClassHierarchyBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubsHierarchy.HierarchyBuilder");
  private static final boolean TEST_MEMORY_USAGE = false;

  public static void build(@NotNull Project project) {
    ProgressManager progress = ProgressManager.getInstance();
    progress.runProcessWithProgressSynchronously(new BuildSingleClassHierarchy(project), "Building Hierarchy", false, project);
  }

  private static class UnitProcessor implements Processor<IndexTree.Unit> {
    private final Project myProject;
    private final HierarchyService myHierarchyService;
    protected final BitSet myProcessedSet = new BitSet();
    private final ProjectFileIndex myProjectIndex;
    boolean isInSourceMode = false;

    private UnitProcessor(Project project, HierarchyService hierarchyService) {
      myProject = project;
      myHierarchyService = hierarchyService;
      myProjectIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    }

    @Override
    public boolean process(IndexTree.Unit unit) {
      VirtualFile file = PersistentFS.getInstance().findFileById(unit.myFileId);
      if (file == null) {
        return true;
      }
      boolean process = isInSourceMode ? myProjectIndex.isInSourceContent(file) : myProjectIndex.isInLibraryClasses(file);
      if (process) {
        myHierarchyService.processUnit(unit);
        myProcessedSet.set(unit.myFileId);
      }
      return true;
    }
  }

  private static class BuildSingleClassHierarchy implements Runnable {
    Project myProject;

    BuildSingleClassHierarchy(Project project) {
      myProject = project;
    }

    @Override
    public void run() {
      HierarchyService service = HierarchyService.instance(myProject);
      if (service.getSingleClassHierarchy() != null) {
        return;
      }
      LOG.info("BuildHierarchy started");
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

      final double classes = 0.3;
      final double completeClasses = 0.1;
      final double sources = 0.3;
      final double collectPsiFilesFraction = 0.2;

      final UnitProcessor processor = new UnitProcessor(myProject, service);

      // 1. entering classes
      LOG.info("read classes start");
      indicator.setText("Reading classes");
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.UNITS, myProject, processor);
        }
      });
      indicator.setFraction(classes);
      testMemory("0");

      // 2. completing classes
      LOG.info("complete classes start");
      indicator.setText("Completing classes");
      service.connect1();
      indicator.setFraction(classes + completeClasses);
      testMemory("1");

      // 3. reading sources
      LOG.info("read sources start");
      indicator.setText("Reading sources");
      processor.isInSourceMode = true;
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.UNITS, myProject, processor);
        }
      });
      indicator.setFraction(classes + completeClasses + sources);


      // 4. reading PSI
      LOG.info("read PSI start");
      indicator.setText("Collecting PSI Files");
      final Set<VirtualFile> srcSet = new HashSet<VirtualFile>();
      collectFiles(srcSet, processor.myProcessedSet);

      double total = srcSet.size();
      LOG.info("Processing PSI");
      indicator.setText("Processing PSI Files");

      int loadedCound = 0;
      for (final VirtualFile vFile : srcSet) {
        String presentableUrl = vFile.getPresentableUrl();
        if (HierarchyService.PROCESS_PSI) {
          PsiFile psiFile =  ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
            @Override
            public PsiFile compute() {
              return PsiManager.getInstance(myProject).findFile(vFile);
            }
          });
          if (psiFile instanceof PsiClassOwner) {
            service.processPsiClassOwner((PsiClassOwner)psiFile);
            LOG.info("PSI: " + presentableUrl);
          }
        }
        loadedCound++;
        indicator.setFraction(classes + completeClasses + sources + collectPsiFilesFraction *(loadedCound / total));
      }
      testMemory("2");

      // 5. completing sources + PSI
      indicator.setText("Completing sources + PSI");
      service.complete2();
      LOG.info("Complete end");
      testMemory("3");
      indicator.setFraction(0.9);

      // 6. connect subtypes
      indicator.setText("Connecting subtypes");
      service.connectSubtypes();
      LOG.info("Subtypes connected");
      indicator.setFraction(1);
    }

    private void collectFiles(final Set<VirtualFile> srcSet, final BitSet processed) {
      final ProjectFileIndex projectIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
      projectIndex.iterateContent(new ContentIterator() {
        @Override
        public boolean processFile(VirtualFile fileOrDir) {
          int fileId = ((VirtualFileWithId)fileOrDir).getId();
          if (!processed.get(fileId) && projectIndex.isInSourceContent(fileOrDir)) {
            srcSet.add(fileOrDir);
          }
          return true;
        }
      });
    }
  }

  private static void testMemory(final String msg) {
    if (!TEST_MEMORY_USAGE)
      return;
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        MessageDialogBuilder.yesNo(msg, msg).show();
      }
    });
  }
}
