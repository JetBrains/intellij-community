// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.intellij.openapi.util.Pair;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Novelty;
import intellij.platform.onair.storage.api.Storage;
import intellij.platform.onair.storage.api.StorageConsumer;
import intellij.platform.onair.storage.cache.ConcurrentObjectCache;
import intellij.platform.onair.tree.BTree;
import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.CachedData;
import net.spy.memcached.ClientMode;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.BulkGetCompletionListener;
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.transcoders.Transcoder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;
import static intellij.platform.onair.tree.ByteUtils.normalizeLowBytes;
import static intellij.platform.onair.tree.ByteUtils.readUnsignedLong;

public class StorageImpl implements Storage, BulkGetCompletionListener {
  private static final HashFunction HASH = Hashing.goodFastHash(128);
  private static final int MAX_VALUE_SIZE = Integer.MAX_VALUE;
  private static final int LOCAL_CACHE_SIZE = Integer.getInteger("intellij.platform.onair.storage.cache", 250000);
  private static final int CLIENT_COUNT = 4;
  private final MemcachedClient[] myClient;
  private final ConcurrentObjectCache<Address, Object> myLocalCache;
  private final AtomicInteger prefetchInProgress = new AtomicInteger(0);
  private final ConcurrentHashMap<Address, byte[]> preFetches = new ConcurrentHashMap<>();

  private final AtomicLong prefetchHits = new AtomicLong(0);
  private final AtomicLong prefetchMisses = new AtomicLong(0);

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

  private static final Transcoder<byte[]> MY_TRANSCODER = new ByteArrayTranscoder();

  int q = 0;

  public StorageImpl(InetSocketAddress socketAddress) throws IOException {
    myClient = new MemcachedClient[CLIENT_COUNT];
    final List<InetSocketAddress> address = Collections.singletonList(socketAddress);
    for (int i = 0; i < CLIENT_COUNT; i++) {
      myClient[i] = new MemcachedClient(new BinaryConnectionFactory(ClientMode.Static, 16384, 16384 * 8), address);
    }
    myLocalCache = new ConcurrentObjectCache<>(LOCAL_CACHE_SIZE);
  }

  @SuppressWarnings("unchecked")
  @Override
  public byte[] lookup(@NotNull Address address) {
    Object result = myLocalCache.get(address);
    if (result == null) {
      prefetchMisses.incrementAndGet();
      result = myClient[0].get(address.toString(), MY_TRANSCODER);
      if (result != null) {
        myLocalCache.put(address, result);
      }
    }
    if (result instanceof byte[]) {
      return (byte[])result;
    }
    if (result instanceof BulkFuture) {
      prefetchHits.incrementAndGet();
      final BulkFuture<Map<String, byte[]>> future = (BulkFuture<Map<String, byte[]>>)result;
      try {
        Map<String, byte[]> data = future.get();
        if (!future.getStatus().isSuccess()) {
          throw new IllegalStateException("prefetch failed");
        }
        final byte[] bytes = data.get(address.toString());
        myLocalCache.put(address, bytes);
        return bytes;
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      catch (ExecutionException e) {
        throw toRuntime(e);
      }
    }
    throw new IllegalArgumentException();
  }

  @NotNull
  public Address alloc(@NotNull byte[] bytes) {
    byte[] hashCode = HASH.hashBytes(bytes).asBytes();
    long lowBytes = readUnsignedLong(hashCode, 0, 8);
    long highBytes = readUnsignedLong(hashCode, 8, 8);
    return new Address(highBytes, normalizeLowBytes(lowBytes));
  }

  @Override
  public void store(@NotNull Address address, @NotNull byte[] bytes) {
    myClient[0].set(address.toString(), 0, bytes, MY_TRANSCODER);
  }

  @Override
  public void prefetch(@NotNull Address prefetchAddress, @NotNull byte[] bytes, @NotNull BTree tree, int size, byte type) {
    if (prefetchInProgress.get() > 70) {
      System.out.println("too many pre-fetches, cool down");
      return;
    }

    if (type == BTree.INTERNAL && myLocalCache.get(prefetchAddress) != null) {
      return;
    }

    if (preFetches.putIfAbsent(prefetchAddress, bytes) != null) {
      return;
    }

    final int bytesPerKey = tree.getKeySize();
    final List<String> addresses = new ArrayList<>(size);
    final List<Address> addressValues = new ArrayList<>(size);

    for (int i = 0; i < size; i++) {
      final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * i + bytesPerKey;
      final long lowBytes = readUnsignedLong(bytes, offset, 8);
      final long highBytes = readUnsignedLong(bytes, offset + 8, 8);
      Address address = new Address(highBytes, lowBytes);
      if (myLocalCache.get(address) == null) { // don't prefetch already cached stuff
        addresses.add(address.toString());
        addressValues.add(address);
      }
    }
    if (addresses.isEmpty()) {
      return; // all pre-fetched
    }
    int index = this.q + 1;
    if (index >= CLIENT_COUNT) {
      index = 0;
    }
    q = index;
    final BulkFuture<Map<String, byte[]>> future = myClient[index].asyncGetBulk(addresses, MY_TRANSCODER);
    prefetchInProgress.incrementAndGet();
    future.addListener(new BulkGetCompletionListener() {
      @Override
      public void onComplete(BulkGetFuture<?> future) throws Exception {
        prefetchInProgress.decrementAndGet();
        preFetches.remove(prefetchAddress);
        Map<String, ?> data = future.get();
        if (future.getStatus().isSuccess()) {
          data.forEach((key, value) -> {
            final int index = addresses.indexOf(key);
            if (index > 0) {
              myLocalCache.put(addressValues.get(index), value);
            }
          });
        }
        else {
          System.out.println("async get failed");
          addressValues.forEach(value -> myLocalCache.remove(value)); // cleanup futures
        }
      }
    });
    addressValues.forEach(value -> myLocalCache.put(value, future));
  }

  @Override
  public void onComplete(BulkGetFuture<?> future) throws Exception {
    // prefetchInProgress.decrementAndGet();
    // future.get().forEach((key, value) -> myLocalCache.put(key, value));
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
        throw toRuntime(e);
      }
    });
    return result;
  }

  public void close() {
    System.out.println("prefetch hits: " + prefetchHits.get() + ", misses: " + prefetchMisses.get());
    for (final MemcachedClient client : myClient) {
      client.shutdown();
    }
  }

  private Future setAll(final int client, @NotNull final List<Pair<Address, byte[]>> list) {
    final Future[] f = new Future[1];
    list.forEach(pair -> f[0] = myClient[client].set(pair.first.toString(), 0, pair.second, MY_TRANSCODER));
    return f[0];
  }

  private static RuntimeException toRuntime(ExecutionException e) {
    Throwable cause = e.getCause();
    return cause instanceof RuntimeException ? (RuntimeException)cause : new RuntimeException(cause);
  }
}
