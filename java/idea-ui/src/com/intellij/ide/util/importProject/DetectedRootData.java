// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.importProject;

import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DetectedRootData {
  private final File myDirectory;
  private final MultiMap<DetectedProjectRoot, ProjectStructureDetector> myRoots = MultiMap.createLinked();

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
    return roots.toArray(new DetectedProjectRoot[0]);
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

  public @NotNull Collection<ProjectStructureDetector> getSelectedDetectors() {
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
