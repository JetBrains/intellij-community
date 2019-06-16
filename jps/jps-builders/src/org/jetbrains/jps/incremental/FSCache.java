// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * @deprecated not used after file traversal rewrite to NIO
 */
@Deprecated
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
  private final Map<File, File[]> myMap = Collections.synchronizedMap(new THashMap<>());

  @Nullable
  public File[] getChildren(File file) {
    final File[] children = myMap.get(file);
    if (children != null) {
      return children == NULL_VALUE? null : children;
    }
    final File[] files = file.listFiles();
    myMap.put(file, files == null? NULL_VALUE : (files.length == 0? EMPTY_FILE_ARRAY : files));
    return files;
  }

  public void clear() {
    myMap.clear();
  }
}
