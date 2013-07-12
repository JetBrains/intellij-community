package com.intellij.compilerOutputIndex.api.descriptor;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class HashSetKeyDescriptor<K> implements KeyDescriptor<Set<K>> {

  private final DataExternalizer<K> keyDataExternalizer;

  public HashSetKeyDescriptor(final DataExternalizer<K> keyDataExternalizer) {
    this.keyDataExternalizer = keyDataExternalizer;
  }

  @Override
  public void save(final DataOutput out, final Set<K> set) throws IOException {
    out.writeInt(set.size());
    for (final K k : set) {
      keyDataExternalizer.save(out, k);
    }
  }

  @Override
  public HashSet<K> read(final DataInput in) throws IOException {
    final int size = in.readInt();
    final HashSet<K> set = new HashSet<K>(size);
    for (int i = 0; i < size; i++) {
      set.add(keyDataExternalizer.read(in));
    }
    return set;
  }

  public static <K> HashSetKeyDescriptor<K> of(final DataExternalizer<K> keyDataExternalizer) {
    return new HashSetKeyDescriptor<K>(keyDataExternalizer);
  }

  @Override
  public int getHashCode(final Set<K> value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(final Set<K> val1, final Set<K> val2) {
    return val1.equals(val2);
  }
}
