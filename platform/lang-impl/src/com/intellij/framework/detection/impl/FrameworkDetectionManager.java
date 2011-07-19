/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl;

import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public class FrameworkDetectionManager extends AbstractProjectComponent implements FrameworkDetectionIndexListener {
  private final Update myDetectionUpdate = new Update("detection") {
    @Override
    public void run() {
      runDetection();
    }
  };
  private final MultiMap<Integer, VirtualFile> myFilesToProcess = new MultiMap<Integer, VirtualFile>();
  private MergingUpdateQueue myDetectionQueue;
  private final Object myLock = new Object();
  private DetectedFrameworksData myDetectedFrameworksData;

  public static FrameworkDetectionManager getInstance(@NotNull Project project) {
    return project.getComponent(FrameworkDetectionManager.class);
  }

  public FrameworkDetectionManager(Project project) {
    super(project);
  }

  @Override
  public void initComponent() {
    myDetectionQueue = new MergingUpdateQueue("FrameworkDetectionQueue", 300, true, null, myProject);
    myDetectedFrameworksData = new DetectedFrameworksData(myProject);
    FrameworkDetectionIndex.getInstance().addListener(this, myProject);
  }

  @Override
  public void disposeComponent() {
    myDetectedFrameworksData.saveDetected();
  }

  @Override
  public void fileUpdated(@NotNull VirtualFile file, @NotNull Integer detectorId) {
    synchronized (myLock) {
      myFilesToProcess.putValue(detectorId, file);
    }
    myDetectionQueue.queue(myDetectionUpdate);
  }

  private void runDetection() {
    MultiMap<Integer, VirtualFile> filesToProcess;
    synchronized (myLock) {
      filesToProcess = new MultiMap<Integer, VirtualFile>();
      filesToProcess.putAllValues(myFilesToProcess);
      myFilesToProcess.clear();
    }

    final FileBasedIndex index = FileBasedIndex.getInstance();
    for (Integer key : filesToProcess.keySet()) {
      Collection<VirtualFile> files = index.getContainingFiles(FrameworkDetectionIndex.NAME, key, GlobalSearchScope.allScope(myProject));
      final Collection<VirtualFile> changed = filesToProcess.get(key);
      changed.retainAll(files);
      myDetectedFrameworksData.retainNewFiles(key, changed);
      FrameworkDetector detector = FrameworkDetectorRegistry.getInstance().getDetectorById(key);
      if (detector != null) {
        detector.detect(files);
      }
    }
  }
}
