package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** @noinspection SynchronizationOnLocalVariableOrMethodParameter*/
final class FilesDelta {
  private final Set<String> myDeletedProduction = Collections.synchronizedSet(new HashSet<String>());
  private final Set<String> myDeletedTests = Collections.synchronizedSet(new HashSet<String>());
  private final Map<File, Set<File>> mySourcesToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>()); // srcRoot -> set of sources
  private final Map<File, Set<File>> myTestsToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>());   // srcRoot -> set of sources

  public void save(DataOutput out, final boolean tests) throws IOException {
    final Set<String> deleted = tests? myDeletedTests : myDeletedProduction;
    out.writeInt(deleted.size());
    for (String path : deleted) {
      IOUtil.writeString(path, out);
    }
    final Map<File, Set<File>> recompile = tests? myTestsToRecompile : mySourcesToRecompile;
    out.writeInt(recompile.size());
    for (Map.Entry<File, Set<File>> entry : recompile.entrySet()) {
      final File root = entry.getKey();
      IOUtil.writeString(FileUtil.toSystemIndependentName(root.getPath()), out);
      final Set<File> files = entry.getValue();
      out.writeInt(files.size());
      for (File file : files) {
        IOUtil.writeString(FileUtil.toSystemIndependentName(file.getPath()), out);
      }
    }
  }

  public void load(DataInput in, final boolean tests) throws IOException {
    final Set<String> deleted = tests? myDeletedTests : myDeletedProduction;
    deleted.clear();
    int deletedCount = in.readInt();
    while (deletedCount-- > 0) {
      deleted.add(IOUtil.readString(in));
    }
    final Map<File, Set<File>> recompile = tests? myTestsToRecompile : mySourcesToRecompile;
    recompile.clear();
    int recompileCount = in.readInt();
    while (recompileCount-- > 0) {
      final File root = new File(IOUtil.readString(in));
      Set<File> files = recompile.get(root);
      if (files == null) {
        files = new HashSet<File>();
        recompile.put(root, files);
      }
      int filesCount = in.readInt();
      while (filesCount-- > 0) {
        files.add(new File(IOUtil.readString(in)));
      }
    }
  }

  public void init(Collection<String> deletedProduction, Collection<String> deletedTests, Map<File, Set<File>> recompileProduction, Map<File, Set<File>> recompileTests) {
    myDeletedProduction.clear();
    myDeletedProduction.addAll(deletedProduction);
    myDeletedTests.clear();
    myDeletedTests.addAll(deletedTests);
    mySourcesToRecompile.clear();
    mySourcesToRecompile.putAll(recompileProduction);
    myTestsToRecompile.clear();
    myTestsToRecompile.putAll(recompileTests);
  }


  public boolean markRecompile(File root, boolean isTestRoot, File file) {
    final boolean added = _addToRecompiled(root, isTestRoot, file);
    if (added) {
      final Set<String> deleted = isTestRoot? myDeletedTests : myDeletedProduction;
      synchronized (deleted) {
        if (!deleted.isEmpty()) { // optimization
          deleted.remove(FileUtil.toCanonicalPath(file.getPath()));
        }
      }
    }
    return added;
  }

  public boolean markRecompileIfNotDeleted(File root, boolean isTestRoot, File file) {
    final Set<String> deleted = isTestRoot? myDeletedTests : myDeletedProduction;
    final boolean isMarkedDeleted;
    synchronized (deleted) {
      isMarkedDeleted = !deleted.isEmpty() && deleted.contains(FileUtil.toCanonicalPath(file.getPath()));
    }
    if (!isMarkedDeleted) {
      return _addToRecompiled(root, isTestRoot, file);
    }
    return false;
  }

  private boolean _addToRecompiled(File root, boolean isTestRoot, File file) {
    final Map<File, Set<File>> toRecompile = isTestRoot ? myTestsToRecompile : mySourcesToRecompile;
    Set<File> files;
    synchronized (toRecompile) {
      files = toRecompile.get(root);
      if (files == null) {
        files = new HashSet<File>();
        toRecompile.put(root, files);
      }
    }
    return files.add(file);
  }

  public void addDeleted(File file, boolean isTest) {
    // ensure the file is no more marked to recompilation
    final Map<File, Set<File>> toRecompile = isTest ? myTestsToRecompile : mySourcesToRecompile;
    synchronized (toRecompile) {
      for (Set<File> files : toRecompile.values()) {
        files.remove(file);
      }
    }
    final Set<String> deleted = isTest? myDeletedTests : myDeletedProduction;
    deleted.add(FileUtil.toCanonicalPath(file.getPath()));
  }

  public void clearDeletedPaths(boolean isTest) {
    final Set<String> deleted = isTest? myDeletedTests : myDeletedProduction;
    deleted.clear();
  }

  public Set<String> getAndClearDeletedPaths(boolean isTest) {
    final Set<String> deleted = isTest? myDeletedTests : myDeletedProduction;
    synchronized (deleted) {
      try {
        return new HashSet<String>(deleted);
      }
      finally {
        deleted.clear();
      }
    }
  }

  public Map<File, Set<File>> getSourcesToRecompile(boolean forTests) {
    return forTests? myTestsToRecompile : mySourcesToRecompile;
  }

  public Set<String> getDeletedPaths(boolean isTest) {
    final Set<String> deleted = isTest ? myDeletedTests : myDeletedProduction;
    synchronized (deleted) {
      return deleted.isEmpty()? Collections.<String>emptySet() : new HashSet<String>(deleted);
    }
  }

  @Nullable
  public Set<File> clearRecompile(File root, boolean isTestRoot) {
    return isTestRoot? myTestsToRecompile.remove(root) : mySourcesToRecompile.remove(root);
  }
}
