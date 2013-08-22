/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compilerOutputIndex.impl;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.util.io.DataExternalizer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class GuavaHashMultiSetExternalizer<K> implements DataExternalizer<Multiset<K>> {
  private final DataExternalizer<K> myKeyDataExternalizer;

  public GuavaHashMultiSetExternalizer(final DataExternalizer<K> keyDataExternalizer) {
    myKeyDataExternalizer = keyDataExternalizer;
  }

  @Override
  public void save(final DataOutput out, final Multiset<K> multiset) throws IOException {
    final Set<Multiset.Entry<K>> entries = multiset.entrySet();
    out.writeInt(entries.size());
    for (final Multiset.Entry<K> entry : entries) {
      myKeyDataExternalizer.save(out, entry.getElement());
      out.writeInt(entry.getCount());
    }
  }

  @Override
  public Multiset<K> read(final DataInput in) throws IOException {
    final int size = in.readInt();
    final Multiset<K> multiset = HashMultiset.create(size);
    for (int i = 0; i < size; i++) {
      multiset.add(myKeyDataExternalizer.read(in), in.readInt());
    }
    return multiset;
  }
}
