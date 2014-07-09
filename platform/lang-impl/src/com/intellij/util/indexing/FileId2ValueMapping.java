/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import gnu.trove.TIntObjectHashMap;

/**
* Created by Maxim.Mossienko on 7/4/2014.
*/
class FileId2ValueMapping<Value> {
  private TIntObjectHashMap<Value> id2ValueMap;
  private ValueContainerImpl<Value> valueContainer;

  FileId2ValueMapping(ValueContainerImpl<Value> _valueContainer) {
    id2ValueMap = new TIntObjectHashMap<Value>();
    valueContainer = _valueContainer;

    for (final ValueContainer.ValueIterator<Value> valueIterator = _valueContainer.getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();

      for (final ValueContainer.IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext();) {
        associateFileIdToValue(intIterator.next(), value);
      }
    }
  }

  void associateFileIdToValue(int fileId, Value value) {
    Value previousValue = id2ValueMap.put(fileId, value);
    if (previousValue != null) {
      valueContainer.removeValue(fileId, previousValue);
    }
  }

  boolean removeFileId(int inputId) {
    Value mapped = id2ValueMap.remove(inputId);
    if (mapped != null) {
      valueContainer.removeValue(inputId, mapped);
    }
    if (DebugAssertions.EXTRA_SANITY_CHECKS) {
      for (final ValueContainer.ValueIterator<Value> valueIterator = valueContainer.getValueIterator(); valueIterator.hasNext();) {
        valueIterator.next();
        DebugAssertions.assertTrue(!valueIterator.getValueAssociationPredicate().contains(inputId));
      }
    }
    return mapped != null;
  }
}
