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
package org.jetbrains.jps.incremental;

import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
* @author Eugene Zhuravlev
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
  private final Map<File, File[]> myMap = Collections.synchronizedMap(new THashMap<File, File[]>());

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
