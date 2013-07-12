package com.intellij.compilerOutputIndex.api.descriptor;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class HashMapKeyDescriptor<K, V> implements KeyDescriptor<Map<K, V>> {

  private final DataExternalizer<K> myKeyDataExternalizer;
  private final DataExternalizer<V> myValueDataExternalizer;

  public HashMapKeyDescriptor(final DataExternalizer<K> keyDataExternalizer, final DataExternalizer<V> valueDataExternalizer) {
    myKeyDataExternalizer = keyDataExternalizer;
    myValueDataExternalizer = valueDataExternalizer;
  }

  @Override
  public void save(final DataOutput out, final Map<K, V> map) throws IOException {
    final int size = map.size();
    out.writeInt(size);
    for (final Map.Entry<K, V> e : map.entrySet()) {
      myKeyDataExternalizer.save(out, e.getKey());
      myValueDataExternalizer.save(out, e.getValue());
    }
  }

  @Override
  public Map<K, V> read(final DataInput in) throws IOException {
    final int size = in.readInt();
    final HashMap<K, V> map = new HashMap<K, V>(size);
    for (int i = 0; i < size; i++) {
      map.put(myKeyDataExternalizer.read(in), myValueDataExternalizer.read(in));
    }
    return map;
  }

  @Override
  public int getHashCode(final Map<K, V> map) {
    return map.hashCode();
  }

  @Override
  public boolean isEqual(final Map<K, V> val1, final Map<K, V> val2) {
    return val1.equals(val2);
  }
}
