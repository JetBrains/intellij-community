// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.intellij.util.containers.SLRUMap;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.tree.ByteUtils;
import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.CachedData;
import net.spy.memcached.ClientMode;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.transcoders.Transcoder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;

import static intellij.platform.onair.tree.ByteUtils.normalizeLowBytes;

public class StorageImpl implements Storage {
  private static final HashFunction HASH = Hashing.goodFastHash(128);
  private static final int MAX_VALUE_SIZE = 1024 * 100;
  private static final int LOCAL_CACHE_SIZE = 1000;
  private final MemcachedClient myClient;
  private final SLRUMap<Address, byte[]> myLocalCache;

  private static class ByteArrayTranscoder implements Transcoder<byte[]> {
    @Override
    public boolean asyncDecode(CachedData data) {
      return false;
    }

    @Override
    public CachedData encode(byte[] bytes) {
      return new CachedData(0, bytes, MAX_VALUE_SIZE);
    }

    @Override
    public byte[] decode(CachedData data) {
      return data.getData();
    }

    @Override
    public int getMaxSize() {
      return MAX_VALUE_SIZE;
    }
  }

  private static final Transcoder<byte[]> myTranscoder = new ByteArrayTranscoder();

  public StorageImpl(InetSocketAddress addr) throws IOException {
    myClient = new MemcachedClient(new BinaryConnectionFactory(ClientMode.Static), Collections.singletonList(addr));
    myLocalCache = new SLRUMap<>(LOCAL_CACHE_SIZE, LOCAL_CACHE_SIZE);
  }

  @Override
  public byte[] lookup(@NotNull Address address) {
    byte[] result = myLocalCache.get(address);
    if (result == null) {
      result = myClient.get(address.toString(), myTranscoder);
      if (result != null) {
        myLocalCache.put(address, result);
      }
    }
    return result;
  }

  @NotNull
  public Address alloc(@NotNull byte[] bytes) {
    byte[] hashCode = HASH.hashBytes(bytes).asBytes();
    long lowBytes = ByteUtils.readUnsignedLong(hashCode, 0, 8);
    long highBytes = ByteUtils.readUnsignedLong(hashCode, 8, 8);
    return new Address(highBytes, normalizeLowBytes(lowBytes));
  }

  @Override
  public void store(@NotNull Address address, @NotNull byte[] bytes) {
    myClient.set(address.toString(), 0, bytes, myTranscoder);
    /*try {
      myClient.set(address.toString(), 0, what, myTranscoder).get();
    }
    catch (InterruptedException e) {
      e.printStackTrace();
    }
    catch (ExecutionException e) {
      e.printStackTrace();
    }*/
  }
}
