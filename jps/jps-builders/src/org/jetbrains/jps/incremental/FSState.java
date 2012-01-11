package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.CompilerExcludes;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.incremental.storage.TimestampStorage;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 12/16/11
 */
public class FSState {
  private final Map<Module, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<Module, FilesDelta>());
  private final Set<Module> myInitialTestsScanPerformed = Collections.synchronizedSet(new HashSet<Module>());
  private final Set<Module> myInitialProductionScanPerformed = Collections.synchronizedSet(new HashSet<Module>());
  private volatile FilesDelta myCurrentRoundDelta;
  private volatile FilesDelta myLastRoundDelta;

  public FSState() {
  }

  public boolean markInitialScanPerformed(Module module, boolean forTests) {
    final Set<Module> map = forTests ? myInitialTestsScanPerformed : myInitialProductionScanPerformed;
    return map.add(module);
    // todo: temp comment
    //return true;
  }

  public void beforeNextRoundStart() {
    myLastRoundDelta = myCurrentRoundDelta;
    myCurrentRoundDelta = new FilesDelta();
  }

  public void clearRoundDeltas() {
    myCurrentRoundDelta = null;
    myLastRoundDelta = null;
  }

  public void clearRecompile(RootDescriptor rd) {
    getDelta(rd.module).clearRecompile(rd.root, rd.isTestRoot);
  }

  public void markDirty(final File file, final RootDescriptor rd, final @Nullable TimestampStorage tsStorage) throws Exception {
    final FilesDelta roundDelta = myCurrentRoundDelta;
    if (roundDelta != null) {
      roundDelta.markRecompile(rd.root, rd.isTestRoot, file);
    }
    final FilesDelta mainDelta = getDelta(rd.module);
    final boolean marked = mainDelta.markRecompile(rd.root, rd.isTestRoot, file);
    if (marked && tsStorage != null) {
      tsStorage.markDirty(file);
    }
  }

  public void markAllUpToDate(final RootDescriptor rd, final TimestampStorage tsStorage, final long compilationStartStamp) throws Exception {
    final FilesDelta delta = getDelta(rd.module);
    final Set<File> files = delta.clearRecompile(rd.root, rd.isTestRoot);
    if (files != null) {
      final CompilerExcludes excludes = rd.module.getProject().getCompilerConfiguration().getExcludes();
      for (File file : files) {
        if (!excludes.isExcluded(file)) {
          final long stamp = file.lastModified();
          if (stamp > compilationStartStamp) {
            // if the file was modified after the compilation had started,
            // do not save the stamp considering file dirty
            delta.markRecompile(rd.root, rd.isTestRoot, file);
          }
          else {
            tsStorage.saveStamp(file, stamp);
          }
        }
        else {
          tsStorage.remove(file);
        }
      }
    }
  }


  public boolean processFilesToRecompile(final Module module, final boolean forTests, final FileProcessor processor) throws Exception {
    final FilesDelta lastRoundDelta = myLastRoundDelta;
    final FilesDelta delta = lastRoundDelta != null? lastRoundDelta : getDelta(module);
    final Map<File, Set<File>> data = delta.getSourcesToRecompile(forTests);
    final CompilerExcludes excludes = module.getProject().getCompilerConfiguration().getExcludes();
    synchronized (data) {
      for (Map.Entry<File, Set<File>> entry : data.entrySet()) {
        final String root = FileUtil.toSystemIndependentName(entry.getKey().getPath());
        for (File file : entry.getValue()) {
          if (excludes.isExcluded(file)) {
            continue;
          }
          if (!processor.apply(module, file, root)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public void registerDeleted(final Module module, final String path, final boolean isTest, @Nullable TimestampStorage tsStorage) throws Exception {
    getDelta(module).addDeleted(path, isTest);
    if (tsStorage != null) {
      tsStorage.remove(new File(path));
    }
  }

  public Collection<String> getDeletedPaths(final Module module, final boolean isTest) {
    final FilesDelta delta = myDeltas.get(module);
    if (delta == null) {
      return Collections.emptyList();
    }
    return delta.getDeletedPaths(isTest);
  }

  public void clearDeletedPaths(final Module module, final boolean isTest) {
    final FilesDelta delta = myDeltas.get(module);
    if (delta != null) {
      delta.clearDeletedPaths(isTest);
    }
  }

  @NotNull
  private FilesDelta getDelta(Module module) {
    synchronized (myDeltas) {
      FilesDelta delta = myDeltas.get(module);
      if (delta == null) {
        delta = new FilesDelta();
        myDeltas.put(module, delta);
      }
      return delta;
    }
  }

  private static final class FilesDelta {
    private final Set<String> myDeletedProduction = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> myDeletedTests = Collections.synchronizedSet(new HashSet<String>());
    private final Map<File, Set<File>> mySourcesToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>()); // srcRoot -> set of sources
    private final Map<File, Set<File>> myTestsToRecompile = Collections.synchronizedMap(new HashMap<File, Set<File>>());   // srcRoot -> set of sources

    public boolean markRecompile(File root, boolean isTestRoot, File file) {
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

    public void addDeleted(String path, boolean isTest) {
      final Set<String> map = isTest? myDeletedTests : myDeletedProduction;
      map.add(path);
    }

    public void clearDeletedPaths(boolean isTest) {
      final Set<String> map = isTest? myDeletedTests : myDeletedProduction;
      map.clear();
    }

    public Map<File, Set<File>> getSourcesToRecompile(boolean forTests) {
      return forTests? myTestsToRecompile : mySourcesToRecompile;
    }

    public Set<String> getDeletedPaths(boolean isTest) {
      final Set<String> set = isTest ? myDeletedTests : myDeletedProduction;
      synchronized (set) {
        return new HashSet<String>(set);
      }
    }

    @Nullable
    public Set<File> clearRecompile(File root, boolean isTestRoot) {
      return isTestRoot? myTestsToRecompile.remove(root) : mySourcesToRecompile.remove(root);
    }
  }
}
