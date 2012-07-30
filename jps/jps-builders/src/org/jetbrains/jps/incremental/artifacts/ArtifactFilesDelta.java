package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class ArtifactFilesDelta {
  private final Set<String> myDeletedPaths = Collections.synchronizedSet(new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY));
  private final Map<Integer, Set<String>> myPathsToRecompile = Collections.synchronizedMap(new HashMap<Integer, Set<String>>());

  public void save(DataOutput out) throws IOException {
    out.writeInt(myDeletedPaths.size());
    for (String path : myDeletedPaths) {
      IOUtil.writeString(path, out);
    }
    out.writeInt(myPathsToRecompile.size());
    for (Map.Entry<Integer, Set<String>> entry : myPathsToRecompile.entrySet()) {
      out.writeInt(entry.getKey());
      Set<String> paths = entry.getValue();
      out.writeInt(paths.size());
      for (String path : paths) {
        IOUtil.writeString(path, out);
      }
    }
  }

  public void load(DataInput in) throws IOException {
    myDeletedPaths.clear();
    int deletedCount = in.readInt();
    while (deletedCount-- > 0) {
      myDeletedPaths.add(IOUtil.readString(in));
    }
    myPathsToRecompile.clear();
    int changedCount = in.readInt();
    while (changedCount-- > 0) {
      int rootIndex = in.readInt();
      int filesCount = in.readInt();
      Set<String> changed = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
      while (filesCount-- > 0) {
        changed.add(IOUtil.readString(in));
      }
      myPathsToRecompile.put(rootIndex, changed);
    }
  }

  public void clearDeletedPaths() {
    myDeletedPaths.clear();
  }

  public boolean hasChanges() {
    return !myPathsToRecompile.isEmpty() || !myDeletedPaths.isEmpty();
  }

  public boolean markRecompile(Integer rootIndex, String filePath) {
    boolean added = addToRecompile(rootIndex, filePath);
    if (added) {
      synchronized (myDeletedPaths) {
        if (!myDeletedPaths.isEmpty()) {
          myDeletedPaths.remove(filePath);
        }
      }
    }
    return added;
  }

  private boolean addToRecompile(Integer rootIndex, String filePath) {
    myDeletedPaths.remove(filePath);
    synchronized (myPathsToRecompile) {
      Set<String> changed = myPathsToRecompile.get(rootIndex);
      if (changed == null) {
        changed = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
        myPathsToRecompile.put(rootIndex, changed);
      }
      return changed.add(filePath);
    }
  }

  public void addDeleted(String filePath) {
    synchronized (myPathsToRecompile) {
      for (Set<String> paths : myPathsToRecompile.values()) {
        paths.remove(filePath);
      }
    }
    myDeletedPaths.add(filePath);
  }

  @Nullable
  public Set<String> clearRecompile(int index) {
    return myPathsToRecompile.remove(index);
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

  public Map<Integer, Set<String>> getFilesToRecompile() {
    return myPathsToRecompile;
  }
}
