// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl;

import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.openapi.application.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class DetectedFrameworksData {
  private static final Logger LOG = Logger.getInstance(DetectedFrameworksData.class);
  private PersistentHashMap<String, IntSet> myExistentFrameworkFiles;
  private final MultiMap<String, DetectedFrameworkDescription> myDetectedFrameworks;
  private final Object myLock = new Object();

  public DetectedFrameworksData(@NotNull Project project) {
    myDetectedFrameworks = new MultiMap<>();
    Path file = ProjectUtil.getProjectCachePath(project, getDetectionDirPath(), true).resolve("files");
    try {
      myExistentFrameworkFiles = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, new TIntHashSetExternalizer());
    }
    catch (IOException e) {
      LOG.info(e);
      IOUtil.deleteAllFilesStartingWith(file);
      try {
        myExistentFrameworkFiles = new PersistentHashMap<>(file, EnumeratorStringDescriptor.INSTANCE, new TIntHashSetExternalizer());
      }
      catch (IOException e1) {
        LOG.error(e1);
      }
    }
  }

  public void saveDetected() {
    try {
      myExistentFrameworkFiles.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public Collection<VirtualFile> retainNewFiles(@NotNull String detectorId, @NotNull Collection<? extends VirtualFile> files) {
    synchronized (myLock) {
      IntSet existentFilesSet = null;
      try {
        existentFilesSet = myExistentFrameworkFiles.get(detectorId);
      }
      catch (IOException e) {
        LOG.info(e);
      }

      List<VirtualFile> newFiles = new ArrayList<>();
      for (VirtualFile file : files) {
        final int fileId = FileBasedIndex.getFileId(file);
        if (existentFilesSet == null || !existentFilesSet.contains(fileId)) {
          newFiles.add(file);
        }
      }
      return newFiles;
    }
  }

  public Set<String> getDetectorsForDetectedFrameworks() {
    synchronized (myLock) {
      return new HashSet<>(myDetectedFrameworks.keySet());
    }
  }

  public Collection<? extends DetectedFrameworkDescription> updateFrameworksList(String detectorId,
                                                                                 Collection<? extends DetectedFrameworkDescription> frameworks) {
    synchronized (myLock) {
      final Collection<DetectedFrameworkDescription> oldFrameworks = myDetectedFrameworks.remove(detectorId);
      myDetectedFrameworks.putValues(detectorId, frameworks);
      if (oldFrameworks != null && !oldFrameworks.isEmpty() && !frameworks.isEmpty()) {
        return ContainerUtil.subtract(frameworks, oldFrameworks);
      }
      return frameworks;
    }
  }

  public void putExistentFrameworkFiles(String id, Collection<? extends VirtualFile> files) {
    synchronized (myLock) {
      IntSet set = null;
      try {
        set = myExistentFrameworkFiles.get(id);
      }
      catch (IOException e) {
        LOG.info(e);
      }
      if (set == null) {
        set = new IntOpenHashSet();
        try {
          myExistentFrameworkFiles.put(id, set);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
      for (VirtualFile file : files) {
        set.add(FileBasedIndex.getFileId(file));
      }
    }
  }

  private static final class TIntHashSetExternalizer implements DataExternalizer<IntSet> {
    @Override
    public void save(@NotNull DataOutput out, IntSet value) throws IOException {
      out.writeInt(value.size());
      final IntIterator iterator = value.iterator();
      while (iterator.hasNext()) {
        out.writeInt(iterator.nextInt());
      }
    }

    @Override
    public IntSet read(@NotNull DataInput in) throws IOException {
      int size = in.readInt();
      final IntSet set = new IntOpenHashSet(size);
      while (size-- > 0) {
        set.add(in.readInt());
      }
      return set;
    }
  }

  private static @NotNull Path getDetectionDirPath() {
    return PathManagerEx.getAppSystemDir().resolve("frameworks").resolve("detection");
  }
}
