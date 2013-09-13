package com.intellij.compilerOutputIndex.api.descriptor;

import com.intellij.util.io.DataExternalizer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class HashSetDataExternalizer<K> implements DataExternalizer<Set<K>> {
  private final DataExternalizer<K> myDataExternalizer;

  public HashSetDataExternalizer(final DataExternalizer<K> myDataExternalizer) {
    this.myDataExternalizer = myDataExternalizer;
  }

  @Override
  public void save(final DataOutput out, final Set<K> set) throws IOException {
    out.writeInt(set.size());
    for (final K k : set) {
      myDataExternalizer.save(out, k);
    }
  }

  @Override
  public HashSet<K> read(final DataInput in) throws IOException {
    final int size = in.readInt();
    final HashSet<K> set = new HashSet<K>(size);
    for (int i = 0; i < size; i++) {
      set.add(myDataExternalizer.read(in));
    }
    return set;
  }
}
