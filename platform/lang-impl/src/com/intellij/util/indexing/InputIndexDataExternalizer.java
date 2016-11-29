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

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
* Created by Maxim.Mossienko on 4/7/2014.
*/
public class InputIndexDataExternalizer<K> implements DataExternalizer<Collection<K>> {
  private final KeyDescriptor<K> myKeyDescriptor;
  private final ID<K, ?> myIndexId;

  public InputIndexDataExternalizer(KeyDescriptor<K> keyDescriptor, ID<K, ?> indexId) {
    myKeyDescriptor = keyDescriptor;
    myIndexId = indexId;
  }

  @Override
  public void save(@NotNull DataOutput out, @NotNull Collection<K> value) throws IOException {
    try {
      DataInputOutputUtil.writeINT(out, value.size());
      for (K key : value) {
        myKeyDescriptor.save(out, key);
      }
    }
    catch (IllegalArgumentException e) {
      throw new IOException("Error saving data for index " + myIndexId, e);
    }
  }

  @NotNull
  @Override
  public Collection<K> read(@NotNull DataInput in) throws IOException {
    try {
      final int size = DataInputOutputUtil.readINT(in);
      final List<K> list = new ArrayList<>(size);
      for (int idx = 0; idx < size; idx++) {
        list.add(myKeyDescriptor.read(in));
      }
      return list;
    }
    catch (IllegalArgumentException e) {
      throw new IOException("Error reading data for index " + myIndexId, e);
    }
  }
}
