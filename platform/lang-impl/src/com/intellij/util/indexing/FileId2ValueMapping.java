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

import com.intellij.util.SmartList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntObjectHashMap;

import java.util.List;

/**
* Created by Maxim.Mossienko on 7/4/2014.
*/
class FileId2ValueMapping<Value> {
  private TIntObjectHashMap<Value> id2ValueMap;
  private ValueContainerImpl<Value> valueContainer;
  private boolean myOnePerFileValidationEnabled = true;

  FileId2ValueMapping(ValueContainerImpl<Value> _valueContainer) {
    id2ValueMap = new TIntObjectHashMap<>();
    valueContainer = _valueContainer;

    TIntArrayList removedFileIdList = null;
    List<Value> removedValueList = null;

    for (final ValueContainer.ValueIterator<Value> valueIterator = _valueContainer.getValueIterator(); valueIterator.hasNext();) {
      final Value value = valueIterator.next();

      for (final ValueContainer.IntIterator intIterator = valueIterator.getInputIdsIterator(); intIterator.hasNext();) {
        int id = intIterator.next();
        Value previousValue = id2ValueMap.put(id, value);
        if (previousValue != null) {  // delay removal of duplicated id -> value mapping since it will affect valueIterator we are using
          if (removedFileIdList == null) {
            removedFileIdList = new TIntArrayList();
            removedValueList = new SmartList<>();
          }
          removedFileIdList.add(id);
          removedValueList.add(previousValue);
        }
      }
    }

    if (removedFileIdList != null) {
      for(int i = 0, size = removedFileIdList.size(); i < size; ++i) {
        valueContainer.removeValue(removedFileIdList.get(i), removedValueList.get(i));
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
    if (DebugAssertions.EXTRA_SANITY_CHECKS && myOnePerFileValidationEnabled) {
      for (final ValueContainer.ValueIterator<Value> valueIterator = valueContainer.getValueIterator(); valueIterator.hasNext();) {
        valueIterator.next();
        DebugAssertions.assertTrue(!valueIterator.getValueAssociationPredicate().contains(inputId));
      }
    }
    return mapped != null;
  }

  public void disableOneValuePerFileValidation() {
    myOnePerFileValidationEnabled = false;
  }
}
