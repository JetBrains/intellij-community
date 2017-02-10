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
package com.intellij.ide.util.importProject;

import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class DetectedRootData {
  private final File myDirectory;
  private MultiMap<DetectedProjectRoot, ProjectStructureDetector> myRoots = MultiMap.createLinked();

  private boolean myIncluded = true;
  private DetectedProjectRoot mySelectedRoot;

  public DetectedRootData(ProjectStructureDetector detector, DetectedProjectRoot root) {
    myDirectory = root.getDirectory();
    mySelectedRoot = root;
    myRoots.putValue(root, detector);
  }

  public File getDirectory() {
    return myDirectory;
  }

  public DetectedProjectRoot addRoot(ProjectStructureDetector detector, DetectedProjectRoot root) {
    for (Map.Entry<DetectedProjectRoot, Collection<ProjectStructureDetector>> entry : myRoots.entrySet()) {
      final DetectedProjectRoot oldRoot = entry.getKey();
      final DetectedProjectRoot combined = oldRoot.combineWith(root);
      if (combined != null) {
        myRoots.remove(oldRoot);
        final Set<ProjectStructureDetector> values = new HashSet<>(entry.getValue());
        values.add(detector);
        myRoots.put(combined, values);
        if (mySelectedRoot == oldRoot) {
          mySelectedRoot = combined;
        }
        return combined;
      }
    }
    myRoots.putValue(root, detector);
    return root;
  }

  public DetectedProjectRoot[] getAllRoots() {
    final Set<DetectedProjectRoot> roots = myRoots.keySet();
    return roots.toArray(new DetectedProjectRoot[roots.size()]);
  }

  public boolean isEmpty() {
    return myRoots.isEmpty();
  }

  public boolean isIncluded() {
    return myIncluded;
  }

  public void setIncluded(boolean included) {
    myIncluded = included;
  }

  @NotNull
  public Collection<ProjectStructureDetector> getSelectedDetectors() {
    return myRoots.get(mySelectedRoot);
  }

  public DetectedProjectRoot getSelectedRoot() {
    return mySelectedRoot;
  }


  public void setSelectedRoot(DetectedProjectRoot root) {
    mySelectedRoot = root;
  }

  public Collection<ProjectStructureDetector> removeRoot(DetectedProjectRoot root) {
    return myRoots.remove(root);
  }
}
