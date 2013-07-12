package com.intellij.compilerOutputIndex.impl;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.compilerOutputIndex.api.indexer.CompilerOutputBaseIndex;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.KeyDescriptor;

import java.util.TreeSet;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public abstract class CompilerOutputBaseGramsIndex<K> extends CompilerOutputBaseIndex<K, Multiset<MethodIncompleteSignature>> {

  protected CompilerOutputBaseGramsIndex(final KeyDescriptor<K> keyDescriptor) {
    super(keyDescriptor, new GuavaHashMultiSetExternalizer<MethodIncompleteSignature>(MethodIncompleteSignature.createKeyDescriptor()));
  }

  public TreeSet<UsageIndexValue> getValues(final K key) {
    try {
      final ValueContainer<Multiset<MethodIncompleteSignature>> valueContainer = myIndex.getData(key);
      final Multiset<MethodIncompleteSignature> rawValues = HashMultiset.create();
      valueContainer.forEach(new ValueContainer.ContainerAction<Multiset<MethodIncompleteSignature>>() {
        @Override
        public boolean perform(final int id, final Multiset<MethodIncompleteSignature> values) {
          for (final Multiset.Entry<MethodIncompleteSignature> entry : values.entrySet()) {
            rawValues.add(entry.getElement(), entry.getCount());
          }
          return true;
        }
      });
      return rawValuesToValues(rawValues);
    } catch (StorageException e) {
      throw new RuntimeException();
    }
  }

  private static TreeSet<UsageIndexValue> rawValuesToValues(final Multiset<MethodIncompleteSignature> rawValues) {
    final TreeSet<UsageIndexValue> values = new TreeSet<UsageIndexValue>();
    for (final Multiset.Entry<MethodIncompleteSignature> entry : rawValues.entrySet()) {
      values.add(new UsageIndexValue(entry.getElement(), entry.getCount()));
    }
    return values;
  }
}
