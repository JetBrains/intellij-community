package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface DataIndexer<Key, Value, Data> {
  @NotNull
  Map<Key,Value> map(Data inputData);
}
