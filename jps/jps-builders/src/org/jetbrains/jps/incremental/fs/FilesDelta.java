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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** @noinspection SynchronizationOnLocalVariableOrMethodParameter*/
final class FilesDelta {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.FilesDelta");

  private final Set<String> myDeletedPaths = Collections.synchronizedSet(new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY));
  private final Map<BuildRootDescriptor, Set<File>> myFilesToRecompile = Collections.synchronizedMap(new HashMap<BuildRootDescriptor, Set<File>>());

  public void save(DataOutput out) throws IOException {
    out.writeInt(myDeletedPaths.size());
    synchronized (myDeletedPaths) {
      for (String path : myDeletedPaths) {
        IOUtil.writeString(path, out);
      }
    }
    synchronized (myFilesToRecompile) {
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
  }

  public void load(DataInput in, @NotNull BuildTarget<?> target, BuildRootIndex buildRootIndex) throws IOException {
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
    return hasPathsToDelete() || hasSourcesToRecompile();
  }


  public boolean markRecompile(BuildRootDescriptor root, File file) {
    final boolean added = _addToRecompiled(root, file);
    if (added) {
      synchronized (myDeletedPaths) {
        if (!myDeletedPaths.isEmpty()) { // optimization
          myDeletedPaths.remove(FileUtil.toCanonicalPath(file.getPath()));
        }
      }
    }
    return added;
  }

  public boolean markRecompileIfNotDeleted(BuildRootDescriptor root, File file) {
    final boolean isMarkedDeleted;
    synchronized (myDeletedPaths) {
      isMarkedDeleted = !myDeletedPaths.isEmpty() && myDeletedPaths.contains(FileUtil.toCanonicalPath(file.getPath()));
    }
    if (!isMarkedDeleted) {
      _addToRecompiled(root, file);
      return true;
    }
    return false;
  }

  private boolean _addToRecompiled(BuildRootDescriptor root, File file) {
    if (Utils.IS_TEST_MODE) {
      LOG.info("Marking dirty: " + file.getPath());
    }

    Set<File> files;
    synchronized (myFilesToRecompile) {
      files = myFilesToRecompile.get(root);
      if (files == null) {
        files = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
        myFilesToRecompile.put(root, files);
      }
      return files.add(file);
    }
  }

  public void addDeleted(File file) {
    // ensure the file is no more marked to recompilation
    synchronized (myFilesToRecompile) {
      for (Set<File> files : myFilesToRecompile.values()) {
        files.remove(file);
      }
    }
    final String path = FileUtil.toCanonicalPath(file.getPath());
    myDeletedPaths.add(path);
    if (Utils.IS_TEST_MODE) {
      LOG.info("Marking deleted: " + path);
    }
  }

  public void clearDeletedPaths() {
    myDeletedPaths.clear();
  }

  public Set<String> getAndClearDeletedPaths() {
    synchronized (myDeletedPaths) {
      try {
        final THashSet<String> _paths = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
        _paths.addAll(myDeletedPaths);
        return _paths;
      }
      finally {
        myDeletedPaths.clear();
      }
    }
  }

  public Map<BuildRootDescriptor, Set<File>> getSourcesToRecompile() {
    return myFilesToRecompile;
  }

  private boolean hasSourcesToRecompile() {
    synchronized (myFilesToRecompile) {
      if(!myFilesToRecompile.isEmpty()) {
        for (Set<File> files : myFilesToRecompile.values()) {
          if (!files.isEmpty()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean hasPathsToDelete() {
    return !myDeletedPaths.isEmpty();
  }

  @Nullable
  public Set<File> clearRecompile(BuildRootDescriptor root) {
    return myFilesToRecompile.remove(root);
  }
}
