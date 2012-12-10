package org.jetbrains.jps.incremental;

import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
* @author Eugene Zhuravlev
*         Date: 12/7/12
*/
public class FSCache {

  public static final FSCache NO_CACHE = new FSCache() {
    @Nullable
    @Override
    public File[] getChildren(File file) {
      return file.listFiles();
    }
  };

  private static final File[] NULL_VALUE = new File[0];
  private static final File[] EMPTY_FILE_ARRAY = new File[0];
  private final Map<File, File[]> myMap = new THashMap<File, File[]>();

  @Nullable
  public File[] getChildren(File file) {
    synchronized (myMap) {
      final File[] children = myMap.get(file);
      if (children != null) {
        return children == NULL_VALUE? null : children;
      }
      final File[] files = file.listFiles();
      myMap.put(file, files == null? NULL_VALUE : (files.length == 0? EMPTY_FILE_ARRAY : files));
      return files;
    }
  }

  public void clear() {
    synchronized (myMap) {
      myMap.clear();
    }
  }
}
