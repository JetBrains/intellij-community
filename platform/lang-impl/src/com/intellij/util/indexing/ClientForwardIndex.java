package com.intellij.util.indexing;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.impl.ForwardIndex;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapBasedForwardIndex;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.DataInputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.nio.ByteBuffer;

public class ClientForwardIndex<K, V> implements ForwardIndex<K, V> {

  private final MapBasedForwardIndex<K, V> myDelegate;
  private final DataExternalizer<Collection<K>> myExternalizer;
  private final ID<K, V> myName;
  private final Set<Integer> myCachedKeys = ContainerUtil.newConcurrentSet();

  public ClientForwardIndex(MapBasedForwardIndex<K, V> delegate,
                            DataExternalizer<Collection<K>> externalizer,
                            ID<K, V> name) {
    myDelegate = delegate;
    myExternalizer = externalizer;
    myName = name;
  }

  @NotNull
  @Override
  public InputDataDiffBuilder<K, V> getDiffBuilder(int inputId) throws IOException {
    ensureCached(inputId);
    return myDelegate.getDiffBuilder(inputId);
  }

  private void ensureCached(int inputId) throws IOException {
    if (!myCachedKeys.contains(inputId)) {
      synchronized (myCachedKeys) {
        if (!myCachedKeys.contains(inputId)) {
          cacheKey(inputId);
          myCachedKeys.add(inputId);
        }
      }
    }
  }

  @Override
  public void putInputData(int inputId, @NotNull Map<K, V> data) throws IOException {
    ensureCached(inputId);
    myDelegate.putInputData(inputId, data);
  }

  private void cacheKey(int id) throws IOException {
    ByteBuffer keys = CassandraIndexTable.getInstance().readForward(myName.toString(), id);
    if (keys != null) {
      Collection<K> read = myExternalizer.read(new DataInputStream(new ByteBufferInputStream(keys)));
      myDelegate.getInputsIndex().put(id, read);
    }
  }

  @Override
  public void flush() {
    myDelegate.flush();
  }

  @Override
  public void clear() throws IOException {
    myDelegate.clear();
    myCachedKeys.clear();
  }

  @Override
  public void close() throws IOException {
    myDelegate.close();
  }
}