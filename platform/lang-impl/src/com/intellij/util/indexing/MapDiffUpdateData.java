/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.util.SystemProperties;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MapDiffUpdateData<Key, Value> extends UpdateData<Key, Value> {
  public static boolean ourDiffUpdateEnabled = SystemProperties.getBooleanProperty("idea.disable.diff.index.update", true);

  private Map<Key, Value> removedOrChangedKeys;
  private Map<Key, Value> addedKeys;

  public MapDiffUpdateData(ID<Key, Value> indexId) {
    super(indexId);
  }

  public static <Key, Value> void iterateAddedKeyAndValues(final int inputId,
                                                           final AddedKeyProcessor<Key, Value> consumer,
                                                           Map<Key, Value> data) throws StorageException {
    if (data instanceof THashMap) {
      // such map often (from IdIndex) contain 100x (avg ~240) of entries, also THashMap have no Entry inside so we optimize for gc too
      final Ref<StorageException> exceptionRef = new Ref<>();
      final boolean b = ((THashMap<Key, Value>)data).forEachEntry(new TObjectObjectProcedure<Key, Value>() {
        @Override
        public boolean execute(Key key, Value value) {
          try {
            consumer.process(key, value, inputId);
          }
          catch (StorageException ex) {
            exceptionRef.set(ex);
            return false;
          }
          return true;
        }
      });
      if (!b) throw exceptionRef.get();
    }
    else {
      for (Map.Entry<Key, Value> entry : data.entrySet()) {
        consumer.process(entry.getKey(), entry.getValue(), inputId);
      }
    }
  }

  public static <Key> void iterateRemovedKeys(Collection<Key> keyCollection, int inputId,
                                                                   RemovedOrUpdatedKeyProcessor<Key> consumer) throws StorageException {
    for (Key key : keyCollection) {
      consumer.process(key, inputId);
    }
  }

  @Override
  public void iterateRemovedOrUpdatedKeys(int inputId, RemovedOrUpdatedKeyProcessor<Key> consumer)
    throws StorageException {
    calcDiff();
    iterateRemovedKeys(removedOrChangedKeys.keySet(), inputId, consumer);
  }

  private static final boolean DO_INFO_DUMP = ApplicationManager.getApplication().isInternal();

  private void calcDiff() throws StorageException {
    if (removedOrChangedKeys != null) return;

    try {
      Map<Key, Value> currentValue = getCurrentValue();
      Map<Key, Value> newValue = getNewValue();

      if (!currentValue.isEmpty()) {
        if (newValue.isEmpty()) {
          // removal from index
          addedKeys = newValue;
          removedOrChangedKeys = currentValue;
          return;
        }
        for (Map.Entry<Key, Value> e : currentValue.entrySet()) {
          Value newValueForKey = newValue.get(e.getKey());

          if (!Comparing.equal(newValueForKey, e.getValue()) ||
              newValueForKey == null && !newValue.containsKey(e.getKey())
            ) {
            if (removedOrChangedKeys == null) removedOrChangedKeys = new THashMap<>();
            removedOrChangedKeys.put(e.getKey(), e.getValue());
            if (newValue.containsKey(e.getKey())) {
              if (addedKeys == null) addedKeys = new THashMap<>();
              addedKeys.put(e.getKey(), newValueForKey);
            }
          }
        }
      }
      else {
        if (newValue.isEmpty()) {
          // before and after map are empty
          addedKeys = newValue;
          removedOrChangedKeys = currentValue;
          return;
        }
      }

      if (!newValue.isEmpty()) {
        if (currentValue.isEmpty()) {
          // initial indexing
          addedKeys = newValue;
          removedOrChangedKeys = currentValue;
          return;
        }
        for (Map.Entry<Key, Value> e : newValue.entrySet()) {
          if (!currentValue.containsKey(e.getKey())) {
            if (addedKeys == null) addedKeys = new THashMap<>();
            addedKeys.put(e.getKey(), e.getValue());
          }
        }
      }

      if (removedOrChangedKeys == null) removedOrChangedKeys = Collections.emptyMap();
      if (addedKeys == null) addedKeys = Collections.emptyMap();

      int totalRequests = requests.incrementAndGet();
      totalRemovals.addAndGet(currentValue.size());
      totalAdditions.addAndGet(newValue.size());
      incrementalAdditions.addAndGet(addedKeys.size());
      incrementalRemovals.addAndGet(removedOrChangedKeys.size());

      if ((totalRequests & 0xFFF) == 0 && DO_INFO_DUMP) {
        Logger.getInstance(getClass()).info("Incremental index diff update:"+requests +
                                            ", removals:" + totalRemovals + "->" + incrementalRemovals +
                                            ", additions:" +totalAdditions + "->" +incrementalAdditions);
      }
      //if (removedOrChangedKeys.size() != currentValue.size() ||
      //    addedKeys.size() != newValue.size()
      //   ) {
      //  int a = 1; // your breakpoint can be here
      //}
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private static final AtomicInteger requests = new AtomicInteger();
  private static final AtomicInteger totalRemovals = new AtomicInteger();
  private static final AtomicInteger totalAdditions = new AtomicInteger();
  private static final AtomicInteger incrementalRemovals = new AtomicInteger();
  private static final AtomicInteger incrementalAdditions = new AtomicInteger();

  protected abstract Map<Key, Value> getNewValue();

  protected abstract Map<Key, Value> getCurrentValue() throws IOException;

  @Override
  public void iterateAddedKeys(int inputId, AddedKeyProcessor<Key, Value> consumer) throws StorageException {
    calcDiff();
    iterateAddedKeyAndValues(inputId, consumer, addedKeys);
  }
}
