// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.importProject;

import com.intellij.framework.detection.impl.FrameworkDetectionProcessor;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.projectWizard.importSources.DetectedContentRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.DetectedSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RootDetectionProcessor {
  private static final Logger LOG = Logger.getInstance(RootDetectionProcessor.class);
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

  public static MultiMap<ProjectStructureDetector, DetectedProjectRoot> createRootsMap(List<? extends DetectedRootData> list) {
    MultiMap<ProjectStructureDetector, DetectedProjectRoot> roots = new MultiMap<>();
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
      myDetectedRoots[i] = new ArrayList<>();
    }

    Set<File> parentDirectories = FileCollectionFactory.createCanonicalFileSet();
    File parent = myBaseDir.getParentFile();
    while (parent != null) {
      parentDirectories.add(parent);
      parent = parent.getParentFile();
    }
    processRecursively(myBaseDir, enabledDetectors, parentDirectories);

    final Map<ProjectStructureDetector, List<DetectedProjectRoot>> result = new LinkedHashMap<>();
    for (int i = 0; i < myDetectors.length; i++) {
      if (!myDetectedRoots[i].isEmpty()) {
        result.put(myDetectors[i], myDetectedRoots[i]);
      }
    }
    return result;
  }

  private List<Pair<File, Integer>> processRecursively(File dir, BitSet enabledDetectors, Set<? super File> parentDirectories) {
    List<Pair<File, Integer>> parentsToSkip = new SmartList<>();

    if (myTypeManager.isFileIgnored(dir.getName())) {
      return parentsToSkip;
    }
    if (myProgressIndicator != null) {
      if (myProgressIndicator.isCanceled()) {
        return parentsToSkip;
      }
      @NlsSafe final String path = dir.getPath();
      myProgressIndicator.setText2(path);
    }

    if (FileSystemUtil.isSymLink(dir)) {
      try {
        if (parentDirectories.contains(dir.getCanonicalFile())) {
          return parentsToSkip;
        }
      }
      catch (IOException ignored) {
      }
    }

    try {
      parentDirectories.add(dir);
      File[] children = dir.listFiles();

      if (children == null) {
        children = ArrayUtilRt.EMPTY_FILE_ARRAY;
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
        if (parentToSkip != null && !FileUtil.filesEqual(parentToSkip, dir)) {
          parentsToSkip.add(Pair.create(parentToSkip, i));
        }
      }

      if (!enabledForChildren.isEmpty()) {
        for (File child : children) {
          if (child.isDirectory() && !FrameworkDetectionProcessor.SKIPPED_DIRECTORIES.contains(child.getName())) {
            final List<Pair<File, Integer>> toSkip = processRecursively(child, enabledForChildren, parentDirectories);
            if (!toSkip.isEmpty()) {
              if (enabledForChildren == enabledDetectors) {
                enabledForChildren = new BitSet();
                enabledForChildren.or(enabledDetectors);
              }
              for (Pair<File, Integer> pair : toSkip) {
                enabledForChildren.set(pair.getSecond(), false);
                if (!FileUtil.filesEqual(pair.getFirst(), dir)) {
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
    finally {
      parentDirectories.remove(dir);
    }
  }

  private static void removeIncompatibleRoots(DetectedProjectRoot root, Map<File, DetectedRootData> rootData) {
    DetectedRootData[] allRoots = rootData.values().toArray(new DetectedRootData[0]);
    for (DetectedRootData child : allRoots) {
      final File childDirectory = child.getDirectory();
      if (FileUtil.isAncestor(root.getDirectory(), childDirectory, true)) {
        for (DetectedProjectRoot projectRoot : child.getAllRoots()) {
          if (!root.canContainRoot(projectRoot)) {
            child.removeRoot(projectRoot);
          }
        }
        if (child.isEmpty()) {
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
      myProgressIndicator.setText2(JavaUiBundle.message("progress.text.processing.0.project.roots", roots.values().size()));
    }

    Map<File, DetectedRootData> rootData = new LinkedHashMap<>();
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

    List<DetectedRootData> dataCollection = mergeContentRoots(rootData);
    if (myProgressIndicator != null) {
      myProgressIndicator.setText2("");
    }
    return dataCollection;
  }

  private List<DetectedRootData> mergeContentRoots(Map<File, DetectedRootData> rootData) {
    LOG.debug(rootData.size() + " roots found, merging content roots");
    boolean hasSourceRoots = false;
    Set<ModuleType> typesToReplace = new HashSet<>();
    Set<ModuleType> moduleTypes = new HashSet<>();
    for (DetectedRootData data : rootData.values()) {
      for (DetectedProjectRoot root : data.getAllRoots()) {
        if (root instanceof DetectedContentRoot) {
          Collections.addAll(typesToReplace, ((DetectedContentRoot)root).getTypesToReplace());
          moduleTypes.add(((DetectedContentRoot)root).getModuleType());
        }
        else if (root instanceof DetectedSourceRoot) {
          LOG.debug("Source root found: " + root.getDirectory() + ", content roots will be ignored");
          hasSourceRoots = true;
          break;
        }
      }
    }
    moduleTypes.removeAll(typesToReplace);

    if (hasSourceRoots || moduleTypes.size() <= 1) {
      Iterator<DetectedRootData> iterator = rootData.values().iterator();
      DetectedContentRoot firstRoot = null;
      ProjectStructureDetector firstDetector = null;
      while (iterator.hasNext()) {
        DetectedRootData data = iterator.next();
        for (DetectedProjectRoot root : data.getAllRoots()) {
          if (root instanceof DetectedContentRoot) {
            LOG.debug("Removed detected " + root.getRootTypeName() + " content root: " + root.getDirectory());
            Collection<ProjectStructureDetector> detectors = data.removeRoot(root);
            if ((firstRoot == null || firstDetector == null) && moduleTypes.contains(((DetectedContentRoot)root).getModuleType())) {
              firstRoot = (DetectedContentRoot)root;
              firstDetector = ContainerUtil.getFirstItem(detectors);
            }
          }
        }
        if (data.isEmpty()) {
          iterator.remove();
        }
      }
      if (!hasSourceRoots && firstRoot != null && firstDetector != null) {
        DetectedContentRoot baseRoot = new DetectedContentRoot(myBaseDir, firstRoot.getRootTypeName(), firstRoot.getModuleType());
        DetectedRootData data = rootData.get(myBaseDir);
        if (data == null) {
          rootData.put(myBaseDir, new DetectedRootData(firstDetector, baseRoot));
        }
        else {
          data.addRoot(firstDetector, baseRoot);
        }
        LOG.debug("Added " + firstRoot.getRootTypeName() + " content root for " + myBaseDir);
      }
    }
    return new ArrayList<>(rootData.values());
  }
}
