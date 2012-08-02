package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.Utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** @noinspection SynchronizationOnLocalVariableOrMethodParameter*/
final class FilesDelta {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.FilesDelta");

  private final Set<String> myDeletedPaths = Collections.synchronizedSet(new HashSet<String>());
  private final Map<File, Set<File>> myFilesToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>()); // srcRoot -> set of sources

  public void save(DataOutput out) throws IOException {
    out.writeInt(myDeletedPaths.size());
    for (String path : myDeletedPaths) {
      IOUtil.writeString(path, out);
    }
    out.writeInt(myFilesToRecompile.size());
    for (Map.Entry<File, Set<File>> entry : myFilesToRecompile.entrySet()) {
      final File root = entry.getKey();
      IOUtil.writeString(FileUtil.toSystemIndependentName(root.getPath()), out);
      final Set<File> files = entry.getValue();
      out.writeInt(files.size());
      for (File file : files) {
        IOUtil.writeString(FileUtil.toSystemIndependentName(file.getPath()), out);
      }
    }
  }

  public void load(DataInput in) throws IOException {
    myDeletedPaths.clear();
    int deletedCount = in.readInt();
    while (deletedCount-- > 0) {
      myDeletedPaths.add(IOUtil.readString(in));
    }
    myFilesToRecompile.clear();
    int recompileCount = in.readInt();
    while (recompileCount-- > 0) {
      final File root = new File(IOUtil.readString(in));
      Set<File> files = myFilesToRecompile.get(root);
      if (files == null) {
        files = createSetOfFiles();
        myFilesToRecompile.put(root, files);
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

  public boolean hasChanges() {
    return hasPathsToDelete() || hasSourcesToRecompile();
  }


  private static Set<File> createSetOfFiles() {
    return new THashSet<File>(new TObjectHashingStrategy<File>() {
      @Override
      public int computeHashCode(File file) {
        return FileUtil.fileHashCode(file);
      }

      @Override
      public boolean equals(File f1, File f2) {
        return FileUtil.filesEqual(f1, f2);
      }
    });
  }

  public boolean markRecompile(File root, File file) {
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

  public boolean markRecompileIfNotDeleted(File root, File file) {
    final boolean isMarkedDeleted;
    synchronized (myDeletedPaths) {
      isMarkedDeleted = !myDeletedPaths.isEmpty() && myDeletedPaths.contains(FileUtil.toCanonicalPath(file.getPath()));
    }
    if (!isMarkedDeleted) {
      return _addToRecompiled(root, file);
    }
    return false;
  }

  private boolean _addToRecompiled(File root, File file) {
    if (Utils.IS_TEST_MODE) {
      LOG.info("Marking dirty: " + file.getPath());
    }

    Set<File> files;
    synchronized (myFilesToRecompile) {
      files = myFilesToRecompile.get(root);
      if (files == null) {
        files = createSetOfFiles();
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
        return new HashSet<String>(myDeletedPaths);
      }
      finally {
        myDeletedPaths.clear();
      }
    }
  }

  public Map<File, Set<File>> getSourcesToRecompile() {
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
  public Set<File> clearRecompile(File root) {
    return myFilesToRecompile.remove(root);
  }
}
