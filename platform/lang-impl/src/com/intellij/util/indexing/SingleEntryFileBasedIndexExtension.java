package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * Base implementation for indices that produce single value per single file
 *
 * @author Eugene Zhuravlev
 *         Date: Feb 18, 2009
 */
public abstract class SingleEntryFileBasedIndexExtension<V> extends CustomImplementationFileBasedIndexExtension<Integer, V, FileContent>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.SingleEntryFileBasedIndexExtension");

  public final KeyDescriptor<Integer> getKeyDescriptor() {
    return new EnumeratorIntegerDescriptor();
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public abstract SingleEntryIndexer<V> getIndexer();

  // optimization: since there is always only one value per file, we can easily
  // get old value from index storage instead of calculating it with the indexer
  public final UpdatableIndex<Integer, V, FileContent> createIndexImplementation(ID<Integer, V> indexId,
                                                                           FileBasedIndex owner,
                                                                           IndexStorage<Integer, V> integerVIndexStorage) {
    return new MapReduceIndex<Integer,V, FileContent>(indexId, getIndexer(), integerVIndexStorage) {
      protected Map<Integer, V> mapOld(FileContent inputData) throws StorageException {
        if (inputData == null) {
          return Collections.emptyMap();
        }
        final int key = Math.abs(FileBasedIndex.getFileId(inputData.getFile()));

        final Map<Integer, V> result = new HashMap<Integer, V>();
        final Lock lock = getReadLock();
        try {
          lock.lock();
          final ValueContainer<V> valueContainer = getData(key);
          if (valueContainer.size() != 1) {
            LOG.assertTrue(valueContainer.size() == 0);
            return result;
          }

          result.put(key, valueContainer.getValueIterator().next());
        }
        finally {
          lock.unlock();
        }

        return result;
      }
    };
  }
}
