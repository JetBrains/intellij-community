package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/** @noinspection SynchronizationOnLocalVariableOrMethodParameter*/
final class FilesDelta {
  private final Set<String> myDeletedProduction = Collections.synchronizedSet(new HashSet<String>());
  private final Set<String> myDeletedTests = Collections.synchronizedSet(new HashSet<String>());
  private final Map<File, Set<File>> mySourcesToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>()); // srcRoot -> set of sources
  private final Map<File, Set<File>> myTestsToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>());   // srcRoot -> set of sources

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
