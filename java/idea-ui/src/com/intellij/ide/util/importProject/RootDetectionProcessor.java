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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class RootDetectionProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.importProject.RootDetectionProcessor");
  private final File myBaseDir;
  private final ProjectStructureDetector[] myDetectors;
  private final List<DetectedProjectRoot>[] myDetectedRoots;
  private final FileTypeManager myTypeManager;
  private final ProgressIndicator myProgressIndicator;

  @NotNull
  public static List<DetectedRootData> detectRoots(@NotNull File baseProjectFile) {
    return new RootDetectionProcessor(baseProjectFile, ProjectStructureDetector.EP_NAME.getExtensions()).detectRoots();
  }

  public RootDetectionProcessor(File baseDir, final ProjectStructureDetector[] detectors) {
    myBaseDir = getCanonicalDir(baseDir);
    myDetectors = detectors;
    //noinspection unchecked
    myDetectedRoots = new List[myDetectors.length];
    myTypeManager = FileTypeManager.getInstance();
    myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
  }

  private static File getCanonicalDir(File baseDir) {
    try {
      return new File(FileUtil.resolveShortWindowsName(baseDir.getAbsolutePath()));
    }
    catch (IOException e) {
      LOG.info(e);
      return baseDir;
    }
  }

  public static MultiMap<ProjectStructureDetector, DetectedProjectRoot> createRootsMap(List<DetectedRootData> list) {
    MultiMap<ProjectStructureDetector, DetectedProjectRoot> roots = new MultiMap<ProjectStructureDetector, DetectedProjectRoot>();
    for (final DetectedRootData rootData : list) {
      for (ProjectStructureDetector detector : rootData.getSelectedDetectors()) {
        roots.putValue(detector, rootData.getSelectedRoot());
      }
    }
    return roots;
  }


  public Map<ProjectStructureDetector, List<DetectedProjectRoot>> runDetectors() {
    if (!myBaseDir.isDirectory()) {
      return Collections.emptyMap();
    }

    BitSet enabledDetectors = new BitSet(myDetectors.length);
    enabledDetectors.set(0, myDetectors.length);
    for (int i = 0; i < myDetectors.length; i++) {
      myDetectedRoots[i] = new ArrayList<DetectedProjectRoot>();
    }
    processRecursively(myBaseDir, enabledDetectors);

    final Map<ProjectStructureDetector, List<DetectedProjectRoot>> result = new LinkedHashMap<ProjectStructureDetector, List<DetectedProjectRoot>>();
    for (int i = 0; i < myDetectors.length; i++) {
      if (!myDetectedRoots[i].isEmpty()) {
        result.put(myDetectors[i], myDetectedRoots[i]);
      }
    }
    return result;
  }

  private List<Pair<File, Integer>> processRecursively(File dir, BitSet enabledDetectors) {
    List<Pair<File, Integer>> parentsToSkip = new SmartList<Pair<File, Integer>>();

    if (myTypeManager.isFileIgnored(dir.getName())) {
      return parentsToSkip;
    }
    if (myProgressIndicator != null) {
      if (myProgressIndicator.isCanceled()) {
        return parentsToSkip;
      }
      myProgressIndicator.setText2(dir.getPath());
    }

    File[] children = dir.listFiles();

    if (children == null) {
      children = ArrayUtil.EMPTY_FILE_ARRAY;
    }

    BitSet enabledForChildren = enabledDetectors;
    for (int i = 0, detectorsLength = myDetectors.length; i < detectorsLength; i++) {
      if (!enabledDetectors.get(i)) continue;

      final ProjectStructureDetector.DirectoryProcessingResult result = myDetectors[i].detectRoots(dir, children, myBaseDir, myDetectedRoots[i]);

      if (!result.isProcessChildren()) {
        if (enabledForChildren == enabledDetectors) {
          enabledForChildren = new BitSet();
          enabledForChildren.or(enabledDetectors);
        }
        enabledForChildren.set(i, false);
      }

      final File parentToSkip = result.getParentToSkip();
      if (parentToSkip != null && !parentToSkip.equals(dir)) {
        parentsToSkip.add(Pair.create(parentToSkip, i));
      }
    }

    if (!enabledForChildren.isEmpty()) {
      for (File child : children) {
        if (child.isDirectory()) {
          final List<Pair<File, Integer>> toSkip = processRecursively(child, enabledForChildren);
          if (!toSkip.isEmpty()) {
            if (enabledForChildren == enabledDetectors) {
              enabledForChildren = new BitSet();
              enabledForChildren.or(enabledDetectors);
            }
            for (Pair<File, Integer> pair : toSkip) {
              enabledForChildren.set(pair.getSecond(), false);
              if (!pair.getFirst().equals(dir)) {
                parentsToSkip.add(pair);
              }
            }
            if (enabledForChildren.isEmpty()) {
              break;
            }
          }
        }
      }
    }
    return parentsToSkip;
  }

  private static void removeIncompatibleRoots(DetectedProjectRoot root, Map<File, DetectedRootData> rootData) {
    DetectedRootData[] allRoots = rootData.values().toArray(new DetectedRootData[rootData.values().size()]);
    for (DetectedRootData child : allRoots) {
      final File childDirectory = child.getDirectory();
      if (FileUtil.isAncestor(root.getDirectory(), childDirectory, true)) {
        for (DetectedProjectRoot projectRoot : child.getAllRoots()) {
          if (!root.canContainRoot(projectRoot)) {
            child.removeRoot(projectRoot);
          }
        }
        if (child.getAllRoots().length == 0) {
          rootData.remove(childDirectory);
        }
      }
    }
  }

  private static boolean isUnderIncompatibleRoot(DetectedProjectRoot root, Map<File, DetectedRootData> rootData) {
    File directory = root.getDirectory().getParentFile();
    while (directory != null) {
      final DetectedRootData data = rootData.get(directory);
      if (data != null) {
        for (DetectedProjectRoot parentRoot : data.getAllRoots()) {
          if (!parentRoot.canContainRoot(root)) {
            return true;
          }
        }
      }
      directory = directory.getParentFile();
    }
    return false;
  }

  private List<DetectedRootData> detectRoots() {
    Map<ProjectStructureDetector, List<DetectedProjectRoot>> roots = runDetectors();
    if (myProgressIndicator != null) {
      myProgressIndicator.setText2("Processing " + roots.values().size() + " project roots...");
    }

    Map<File, DetectedRootData> rootData = new LinkedHashMap<File, DetectedRootData>();
    for (ProjectStructureDetector detector : roots.keySet()) {
      for (DetectedProjectRoot detectedRoot : roots.get(detector)) {
        if (isUnderIncompatibleRoot(detectedRoot, rootData)) {
          continue;
        }

        final DetectedRootData data = rootData.get(detectedRoot.getDirectory());
        if (data == null) {
          rootData.put(detectedRoot.getDirectory(), new DetectedRootData(detector, detectedRoot));
        }
        else {
          detectedRoot = data.addRoot(detector, detectedRoot);
        }
        removeIncompatibleRoots(detectedRoot, rootData);
      }
    }

    if (myProgressIndicator != null) {
      myProgressIndicator.setText2("");
    }
    return new ArrayList<DetectedRootData>(rootData.values());
  }
}
