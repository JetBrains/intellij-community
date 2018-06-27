// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import com.intellij.util.containers.SLRUMap;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;
import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.CachedData;
import net.spy.memcached.ClientMode;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.transcoders.Transcoder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;

public class StorageImpl implements Storage {
  private static final int MAX_VALUE_SIZE = 1024 * 1024 * 10;
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
  public byte[] lookup(Address address) {
    byte[] result = myLocalCache.get(address);
    if (result == null) {
      result = myClient.get(address.toString(), myTranscoder);
      if (result != null) {
        myLocalCache.put(address, result);
      }
    }
    return result;
  }

  @Override
  public Address store(byte[] what) {
    final Address address = new Address(0, Arrays.hashCode(what));
    myClient.set(address.toString(), 0, what, myTranscoder);
    return address;
  }
}
