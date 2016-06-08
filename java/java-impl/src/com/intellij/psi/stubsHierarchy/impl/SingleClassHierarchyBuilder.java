/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubsHierarchy.stubs.Unit;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class SingleClassHierarchyBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubsHierarchy.HierarchyBuilder");
  private static final boolean TEST_MEMORY_USAGE = false;
  private static final TObjectHashingStrategy<Unit> UNIT_HASHING_STRATEGY = new TObjectHashingStrategy<Unit>() {
    @Override
    public int computeHashCode(Unit object) {
      return object.myClasses[0].myClassAnchor.myFileId;
    }

    @Override
    public boolean equals(Unit o1, Unit o2) {
      return computeHashCode(o1) == computeHashCode(o2);
    }
  };

  public static void build(@NotNull Project project) {
    ProgressManager progress = ProgressManager.getInstance();
    progress.runProcessWithProgressSynchronously(() -> ReadAction.run(() -> new BuildSingleClassHierarchy(project).run()),
                                                 "Building Hierarchy", false, project);
  }

  private static class BuildSingleClassHierarchy implements Runnable {
    Project myProject;
    private ProjectFileIndex myFileIndex;

    BuildSingleClassHierarchy(Project project) {
      myProject = project;
      myFileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    }

    @Override
    public void run() {
      HierarchyService service = HierarchyService.instance(myProject);
      service.clear();

      LOG.info("BuildHierarchy started");
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

      // 1. entering classes
      LOG.info("read classes start");
      processUnits(service, false);
      indicator.setFraction(0.3);
      testMemory("0");

      // 2. completing classes
      LOG.info("complete classes start");
      service.connect1();
      indicator.setFraction(0.4);
      testMemory("1");

      // 3. reading sources
      LOG.info("read sources start");
      processUnits(service, true);
      indicator.setFraction(0.7);

      // 4. completing sources
      service.complete2();
      LOG.info("Complete end");
      testMemory("2");
      indicator.setFraction(0.9);

      // 5. connect subtypes
      service.connectSubtypes();
      LOG.info("Subtypes connected");
    }

    private void processUnits(HierarchyService service, final boolean sourceMode) {
      Set<Unit> result = new THashSet<>(UNIT_HASHING_STRATEGY);
      StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.UNITS, myProject, unit -> {
        Unit compact = shouldProcess(sourceMode, unit.myFileId) ? service.compact(unit) : null;
        if (compact != null && compact.myClasses.length > 0) {
          result.remove(compact); // there can be several (outdated) stub keys for the same file id, only the last one counts
          result.add(compact);
        }
        return true;
      });
      result.forEach(service::processUnit);
    }

    private boolean shouldProcess(boolean sourceMode, final int fileId) {
      VirtualFile file = PersistentFS.getInstance().findFileById(fileId);
      if (file == null) {
        return false;
      }
      return sourceMode ? myFileIndex.isInSourceContent(file) : myFileIndex.isInLibraryClasses(file);
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
