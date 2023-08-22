// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.Utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public final class FilesDelta {
  private static final Logger LOG = Logger.getInstance(FilesDelta.class);
  private final ReentrantLock myDataLock = new ReentrantLock();

  private final Set<String> myDeletedPaths = CollectionFactory.createFilePathLinkedSet();
  private final Map<BuildRootDescriptor, Set<File>> myFilesToRecompile = new LinkedHashMap<>();

  public void lockData(){
    myDataLock.lock();
  }

  public void unlockData(){
    myDataLock.unlock();
  }

  public FilesDelta() {
  }

  FilesDelta(Collection<FilesDelta> deltas) {
    for (FilesDelta delta : deltas) {
      addAll(delta);
    }
  }

  private void addAll(FilesDelta other) {
    other.lockData();
    try {
      myDeletedPaths.addAll(other.myDeletedPaths);
      for (Map.Entry<BuildRootDescriptor, Set<File>> entry : other.myFilesToRecompile.entrySet()) {
        _addToRecompiled(entry.getKey(), entry.getValue());
      }
    }
    finally {
      other.unlockData();
    }
  }

  public void save(DataOutput out) throws IOException {
    lockData();
    try {
      out.writeInt(myDeletedPaths.size());
      for (String path : myDeletedPaths) {
        IOUtil.writeString(path, out);
      }
      out.writeInt(myFilesToRecompile.size());
      for (Map.Entry<BuildRootDescriptor, Set<File>> entry : myFilesToRecompile.entrySet()) {
        IOUtil.writeString(entry.getKey().getRootId(), out);
        final Set<File> files = entry.getValue();
        out.writeInt(files.size());
        for (File file : files) {
          IOUtil.writeString(FileUtil.toSystemIndependentName(file.getPath()), out);
        }
      }
    }
    finally {
      unlockData();
    }
  }

  public void load(DataInput in, @NotNull BuildTarget<?> target, BuildRootIndex buildRootIndex) throws IOException {
    lockData();
    try {
      myDeletedPaths.clear();
      int deletedCount = in.readInt();
      while (deletedCount-- > 0) {
        myDeletedPaths.add(IOUtil.readString(in));
      }
      myFilesToRecompile.clear();
      int recompileCount = in.readInt();
      while (recompileCount-- > 0) {
        String rootId = IOUtil.readString(in);
        BuildRootDescriptor descriptor = target.findRootDescriptor(rootId, buildRootIndex);
        Set<File> files;
        if (descriptor != null) {
          files = myFilesToRecompile.get(descriptor);
          if (files == null) {
            files = FileCollectionFactory.createCanonicalFileLinkedSet();
            myFilesToRecompile.put(descriptor, files);
          }
        }
        else {
          LOG.debug("Cannot find root by " + rootId + ", delta will be skipped");
          files = FileCollectionFactory.createCanonicalFileLinkedSet();
        }
        int filesCount = in.readInt();
        while (filesCount-- > 0) {
          final File file = new File(IOUtil.readString(in));
          if (Utils.IS_TEST_MODE) {
            LOG.info("Loaded " + file.getPath());
          }
          files.add(file);
        }
      }
    }
    finally {
      unlockData();
    }
  }

  public static void skip(DataInput in) throws IOException {
    int deletedCount = in.readInt();
    while (deletedCount-- > 0) {
      IOUtil.readString(in);
    }
    int recompiledCount = in.readInt();
    while (recompiledCount-- > 0) {
      IOUtil.readString(in);
      int filesCount = in.readInt();
      while (filesCount-- > 0) {
        IOUtil.readString(in);
      }
    }
  }

  public boolean hasChanges() {
    lockData();
    try {
      if (!myDeletedPaths.isEmpty()) {
        return true;
      }
      if(!myFilesToRecompile.isEmpty()) {
        for (Set<File> files : myFilesToRecompile.values()) {
          if (!files.isEmpty()) {
            return true;
          }
        }
      }
      return false;
    }
    finally {
      unlockData();
    }
  }


  public boolean markRecompile(BuildRootDescriptor root, File file) {
    lockData();
    try {
      final boolean added = _addToRecompiled(root, file);
      if (added) {
        if (!myDeletedPaths.isEmpty()) { // optimization
          myDeletedPaths.remove(FileUtil.toCanonicalPath(file.getPath()));
        }
      }
      return added;
    }
    finally {
      unlockData();
    }
  }

  public boolean markRecompileIfNotDeleted(BuildRootDescriptor root, File file) {
    lockData();
    try {
      String path = null;
      final boolean isMarkedDeleted = !myDeletedPaths.isEmpty() && myDeletedPaths.contains(path = FileUtil.toCanonicalPath(file.getPath()));
      if (!isMarkedDeleted) {
        if (!file.exists()) {
          // incorrect paths data recovery, so that the next make should not contain non-existing sources in 'recompile' list
          if (path == null) {
            path = FileUtil.toCanonicalPath(file.getPath());
          }
          if (Utils.IS_TEST_MODE) {
            LOG.info("Marking deleted: " + path);
          }
          myDeletedPaths.add(path);
          return false;
        }
        _addToRecompiled(root, file);
        return true;
      }
      return false;
    }
    finally {
      unlockData();
    }
  }

  private boolean _addToRecompiled(BuildRootDescriptor root, File file) {
    if (Utils.IS_TEST_MODE) {
      LOG.info("Marking dirty: " + file.getPath());
    }
    return _addToRecompiled(root, Collections.singleton(file));
  }

  private boolean _addToRecompiled(BuildRootDescriptor root, Collection<? extends File> filesToAdd) {
    Set<File> files = myFilesToRecompile.get(root);
    if (files == null) {
      files = FileCollectionFactory.createCanonicalFileLinkedSet();
      myFilesToRecompile.put(root, files);
    }
    return files.addAll(filesToAdd);
  }

  public void addDeleted(File file) {
    final String path = FileUtil.toCanonicalPath(file.getPath());
    lockData();
    try {
      // ensure the file is not marked to recompilation anymore
      for (Set<File> files : myFilesToRecompile.values()) {
        files.remove(file);
      }
      myDeletedPaths.add(path);
      if (Utils.IS_TEST_MODE) {
        LOG.info("Marking deleted: " + path);
      }
    }
    finally {
      unlockData();
    }
  }

  public void clearDeletedPaths() {
    lockData();
    try {
      myDeletedPaths.clear();
    }
    finally {
      unlockData();
    }
  }

  public Set<String> getAndClearDeletedPaths() {
    lockData();
    try {
      try {
        Set<String> result = CollectionFactory.createFilePathLinkedSet();
        result.addAll(myDeletedPaths);
        return result;
      }
      finally {
        myDeletedPaths.clear();
      }
    }
    finally {
      unlockData();
    }
  }

  @NotNull
  public Map<BuildRootDescriptor, Set<File>> getSourcesToRecompile() {
    LOG.assertTrue(myDataLock.isHeldByCurrentThread(), "FilesDelta data must be locked by querying thread");
    return myFilesToRecompile;
  }

  public boolean isMarkedRecompile(BuildRootDescriptor rd, File file) {
    lockData();
    try {
      final Set<File> files = myFilesToRecompile.get(rd);
      return files != null && files.contains(file);
    }
    finally {
      unlockData();
    }
  }

  @Nullable
  public Set<File> clearRecompile(BuildRootDescriptor root) {
    lockData();
    try {
      return myFilesToRecompile.remove(root);
    }
    finally {
      unlockData();
    }
  }
}
