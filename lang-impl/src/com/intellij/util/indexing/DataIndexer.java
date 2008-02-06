package com.intellij.util.indexing;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface DataIndexer<Key, Value, Data> {
  Map<Key,Value> map(Data inputData);
}
