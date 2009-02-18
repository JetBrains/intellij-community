package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Simplifies API and ensures that data key will always be equal to virtual file id
 *
 * @author Eugene Zhuravlev
 *         Date: Feb 18, 2009
 */
public abstract class SingleEntryIndexer<V> implements DataIndexer<Integer, V, FileContent>{
  private boolean myAcceptNullValues;

  protected SingleEntryIndexer(boolean acceptNullValues) {
    myAcceptNullValues = acceptNullValues;
  }

  @NotNull
  public final Map<Integer, V> map(FileContent inputData) {
    if (inputData == null) {
      return Collections.emptyMap();
    }
    final V value = computeValue(inputData);
    if (value == null && !myAcceptNullValues) {
      return Collections.emptyMap();
    }
    final int key = Math.abs(FileBasedIndex.getFileId(inputData.getFile()));
    return Collections.singletonMap(key, value);
  }

  protected abstract @Nullable V computeValue(@NotNull FileContent inputData);
}
