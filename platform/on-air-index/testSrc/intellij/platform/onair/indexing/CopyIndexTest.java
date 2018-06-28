// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.indexing;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.intellij.psi.stubs.StubIdList;
import com.intellij.util.io.*;
import intellij.platform.onair.storage.DummyStorageImpl;
import intellij.platform.onair.storage.api.NoveltyImpl;
import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.tree.BTree;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;

public class CopyIndexTest {
  private static final HashFunction HASH = Hashing.goodFastHash(128);

  @Test
  public void testAll() throws IOException {
    final Storage storage = DummyStorageImpl.INSTANCE;
    final NoveltyImpl novelty = new NoveltyImpl(new File("/home/morj/index-sandbox/novelty/novelty.dat"));
    try {
      final PersistentHashMap<String, StubIdList> map = new PersistentHashMap<>(
        new File("/home/morj/index-sandbox/idea/java.class.shortname.storage"),
        EnumeratorStringDescriptor.INSTANCE,
        EXT
      );
      try {
        final BTree tree = BTree.create(novelty, storage, 16);
        final AtomicLong size = new AtomicLong();
        map.processKeys(key -> {
          try {
            size.addAndGet(key.length());
            StubIdList list = map.get(key);
            int listSize = list.size();
            size.addAndGet(listSize * 8);
            byte[] valueBytes = key.getBytes(Charset.forName("UTF-8"));
            HashCode hashCode = HASH.hashBytes(valueBytes);
            tree.put(novelty, hashCode.asBytes(), valueBytes);
            return true;
          }
          catch (IOException e) {
            return false;
          }
        });
        System.out.println(size.get());
        System.out.println(novelty.getSize());
      }
      finally {
        map.close();
      }
    }
    finally {
      novelty.close();
    }
  }

  private static DataExternalizer<StubIdList> EXT = new DataExternalizer<StubIdList>() {
    @Override
    public void save(@NotNull final DataOutput out, @NotNull final StubIdList value) throws IOException {
      int size = value.size();
      if (size == 0) {
        DataInputOutputUtil.writeINT(out, Integer.MAX_VALUE);
      }
      else if (size == 1) {
        DataInputOutputUtil.writeINT(out, value.get(0)); // most often case
      }
      else {
        DataInputOutputUtil.writeINT(out, -size);
        for (int i = 0; i < size; ++i) {
          DataInputOutputUtil.writeINT(out, value.get(i));
        }
      }
    }

    @NotNull
    @Override
    public StubIdList read(@NotNull final DataInput in) throws IOException {
      int size = DataInputOutputUtil.readINT(in);
      if (size == Integer.MAX_VALUE) {
        return new StubIdList();
      }
      else if (size >= 0) {
        return new StubIdList(size);
      }
      else {
        size = -size;
        int[] result = new int[size];
        for (int i = 0; i < size; ++i) {
          result[i] = DataInputOutputUtil.readINT(in);
        }
        return new StubIdList(result, size);
      }
    }
  };
}
