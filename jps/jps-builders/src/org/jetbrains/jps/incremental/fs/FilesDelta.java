/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashSet;
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

/** @noinspection SynchronizationOnLocalVariableOrMethodParameter*/
public final class FilesDelta {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.FilesDelta");
  private final ReentrantLock myDataLock = new ReentrantLock();

  private final Set<String> myDeletedPaths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
  private final Map<BuildRootDescriptor, Set<File>> myFilesToRecompile = new HashMap<BuildRootDescriptor, Set<File>>();

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
            files = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
            myFilesToRecompile.put(descriptor, files);
          }
        }
        else {
          LOG.debug("Cannot find root by " + rootId + ", delta will be skipped");
          files = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
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
      final boolean isMarkedDeleted = !myDeletedPaths.isEmpty() && myDeletedPaths.contains(FileUtil.toCanonicalPath(file.getPath()));
      if (!isMarkedDeleted) {
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

  private boolean _addToRecompiled(BuildRootDescriptor root, Collection<File> filesToAdd) {
    Set<File> files = myFilesToRecompile.get(root);
    if (files == null) {
      files = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
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
        final THashSet<String> _paths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
        _paths.addAll(myDeletedPaths);
        return _paths;
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
