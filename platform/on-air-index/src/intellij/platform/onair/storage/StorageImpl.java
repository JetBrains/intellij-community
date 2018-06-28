// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.SLRUMap;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Novelty;
import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.storage.api.StorageConsumer;
import intellij.platform.onair.tree.BTree;
import intellij.platform.onair.tree.ByteUtils;
import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.CachedData;
import net.spy.memcached.ClientMode;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.transcoders.Transcoder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static intellij.platform.onair.tree.ByteUtils.normalizeLowBytes;

public class StorageImpl implements Storage {
  private static final HashFunction HASH = Hashing.goodFastHash(128);
  private static final int MAX_VALUE_SIZE = 1024 * 100;
  private static final int LOCAL_CACHE_SIZE = 1000;
  private static final int CLIENT_COUNT = 4;
  private final MemcachedClient[] myClient;
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
    myClient = new MemcachedClient[CLIENT_COUNT];
    final List<InetSocketAddress> address = Collections.singletonList(addr);
    for (int i = 0; i < CLIENT_COUNT; i++) {
      myClient[i] = new MemcachedClient(new BinaryConnectionFactory(ClientMode.Static, 163840, 16384 * 1024), address);
    }
    myLocalCache = new SLRUMap<>(LOCAL_CACHE_SIZE, LOCAL_CACHE_SIZE);
  }

  @Override
  public byte[] lookup(@NotNull Address address) {
    byte[] result = myLocalCache.get(address);
    if (result == null) {
      result = myClient[0].get(address.toString(), myTranscoder);
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
    myClient[0].set(address.toString(), 0, bytes, myTranscoder);
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

  @SuppressWarnings("WaitNotInLoop")
  public Address bulkStore(@NotNull BTree tree, @NotNull Novelty novelty) {
    final int packSize = 10000;
    final List<Pair<Address, byte[]>> data = new ArrayList<>(packSize);
    final Set<Future> packs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    final StorageConsumer consumer = new StorageConsumer() {
      int client = 0;

      @Override
      public void store(@NotNull Address address, @NotNull byte[] bytes) {
        Address anotherAddress = alloc(bytes);
        if (!address.equals(anotherAddress)) {
          throw new IllegalStateException();
        }
        data.add(new Pair<>(address, Arrays.copyOf(bytes, bytes.length))); // TODO: don't copy?
        if (data.size() == packSize) {
          Future future = setAll(client, new ArrayList<>(data));
          packs.add(future);
          if (++client >= CLIENT_COUNT) {
            client = 0;
          }
          data.clear();
        }
      }
    };
    final Address result = tree.store(novelty, consumer);
    if (!data.isEmpty()) {
      packs.add(setAll(0, data));
    }
    packs.forEach(future -> {
      try {
        future.get();
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException();
      }
      catch (ExecutionException e) {
        Throwable cause = e.getCause();
        throw cause instanceof RuntimeException ? (RuntimeException)cause : new RuntimeException(cause);
      }
    });
    return result;
  }

  public void close() {
    for (final MemcachedClient client : myClient) {
      client.shutdown();
    }
  }

  private Future setAll(final int client, @NotNull final List<Pair<Address, byte[]>> list) {
    final Future[] f = new Future[1];
    list.forEach(pair -> f[0] = myClient[client].set(pair.first.toString(), 0, pair.second, myTranscoder));
    return f[0];
  }
}
