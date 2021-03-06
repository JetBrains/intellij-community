// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.framework.detection.impl;

import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.FileContentImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class FrameworkDetectionProcessor {
  public static final Set<String> SKIPPED_DIRECTORIES = Collections.singleton("node_modules");

  private static final Logger LOG = Logger.getInstance(FrameworkDetectionProcessor.class);
  private final ProgressIndicator myProgressIndicator;
  private final MultiMap<FileType, FrameworkDetectorData> myDetectorsByFileType;
  private Set<VirtualFile> myProcessedFiles;

  private final FrameworkDetectionContext myContext;

  public FrameworkDetectionProcessor(ProgressIndicator progressIndicator, FrameworkDetectionContext context) {
    myProgressIndicator = progressIndicator;
    myDetectorsByFileType = new MultiMap<>();
    for (FrameworkDetector detector : FrameworkDetector.EP_NAME.getExtensionList()) {
      myDetectorsByFileType.putValue(detector.getFileType(), new FrameworkDetectorData(detector));
    }
    myContext = context;
  }

  public List<? extends DetectedFrameworkDescription> processRoots(List<? extends File> roots) {
    myProcessedFiles = new HashSet<>();
    for (File root : roots) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
      if (virtualFile == null) continue;
      collectSuitableFiles(virtualFile);
    }
    List<DetectedFrameworkDescription> result = new ArrayList<>();
    for (FrameworkDetectorData data : myDetectorsByFileType.values()) {
      result.addAll(data.myDetector.detect(data.mySuitableFiles, myContext));
    }
    return FrameworkDetectionUtil.removeDisabled(result);
  }

  private void collectSuitableFiles(@NotNull VirtualFile file) {
    try {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          // Since this code is invoked from New Project Wizard it's very possible that VFS isn't loaded to memory yet, so we need to do it
          // manually, otherwise refresh will do nothing
          myProgressIndicator.checkCanceled();
          return !(file.isDirectory() && SKIPPED_DIRECTORIES.contains(file.getName()));
        }
      });
      file.refresh(false, true);

      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          myProgressIndicator.checkCanceled();
          if (file.isDirectory() && SKIPPED_DIRECTORIES.contains(file.getName()) || !myProcessedFiles.add(file)) {
            return false;
          }

          if (!file.isDirectory()) {
            final FileType fileType = file.getFileType();
            if (myDetectorsByFileType.containsKey(fileType)) {
              myProgressIndicator.setText2(file.getPresentableUrl());
              try {
                final FileContent fileContent = FileContentImpl.createByFile(file, myContext.getProject());
                for (FrameworkDetectorData detector : myDetectorsByFileType.get(fileType)) {
                  if (detector.myFilePattern.accepts(fileContent)) {
                    detector.mySuitableFiles.add(file);
                  }
                }
              }
              catch (IOException e) {
                LOG.info(e);
              }
            }
          }

          return true;
        }
      });
    }
    catch (ProcessCanceledException ignored) {
    }
  }

  private static final class FrameworkDetectorData {
    private final FrameworkDetector myDetector;
    private final ElementPattern<FileContent> myFilePattern;
    private final List<VirtualFile> mySuitableFiles = new ArrayList<>();

    FrameworkDetectorData(FrameworkDetector detector) {
      myDetector = detector;
      myFilePattern = detector.createSuitableFilePattern();
    }
  }
}
