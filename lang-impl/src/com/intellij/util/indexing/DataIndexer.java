package com.intellij.util.indexing;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface DataIndexer<Key, Value, Data> {
  void map(Data inputData, final IndexDataConsumer<Key, Value> consumer);
}
