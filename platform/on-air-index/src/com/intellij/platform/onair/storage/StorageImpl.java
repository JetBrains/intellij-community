// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.onair.storage.api.*;
import com.intellij.platform.onair.tree.BTree;
import net.spy.memcached.*;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.BulkGetCompletionListener;
import net.spy.memcached.internal.BulkGetFuture;
import net.spy.memcached.ops.GetOperation;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.ops.StatusCode;
import net.spy.memcached.transcoders.Transcoder;
import net.spy.memcached.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;
import static com.intellij.platform.onair.tree.ByteUtils.normalizeLowBytes;
import static com.intellij.platform.onair.tree.ByteUtils.readUnsignedLong;

public class StorageImpl implements Storage {
  private static final HashFunction HASH = Hashing.goodFastHash(128);
  private static final int MAX_VALUE_SIZE = Integer.MAX_VALUE;
  private static final int LOCAL_CACHE_SIZE = Integer.getInteger("intellij.platform.onair.storage.cache", 4000000);
  private static final int CLIENT_COUNT = 5;
  private final HackMemcachedClient[] myClient;
  private final Cache<Address, Object> myLocalCache;
  private final AtomicInteger prefetchInProgress = new AtomicInteger(0);
  private final ConcurrentHashMap<Address, byte[]> preFetches = new ConcurrentHashMap<>();

  private static final Transcoder<byte[]> MY_TRANSCODER = new ByteArrayTranscoder();
  // private static final SingleElementInfiniteIterator<Transcoder<byte[]>> MY_TC_ITERATOR = new SingleElementInfiniteIterator<>(MY_TRANSCODER);

  private final AtomicLong prefetchHits = new AtomicLong(0);

  int q = 0;

  public StorageImpl(InetSocketAddress socketAddress) throws IOException {
    myClient = createClients(socketAddress);
    myLocalCache = CacheBuilder
      .newBuilder()
      .recordStats()
      .maximumSize(LOCAL_CACHE_SIZE).build();
  }

  private StorageImpl(HackMemcachedClient[] client, Cache<Address, Object> localCache) {
    myClient = client;
    myLocalCache = localCache;
  }

  public StorageImpl withCache(Cache<Address, Object> cache) {
    return new StorageImpl(myClient, cache);
  }

  @NotNull
  @SuppressWarnings("unchecked")
  @Override
  public byte[] lookup(@NotNull final Address address) {
    try {
      Object result = myLocalCache.get(address, () -> {
        final byte[] remoteResult = myClient[0].get(address.toString(), MY_TRANSCODER);
        if (remoteResult == null) {
          throw new NoSuchElementException("page not found");
        }
        return remoteResult;
      });
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
      throw new NoSuchElementException("page not found");
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public Address alloc(@NotNull byte[] bytes) {
    byte[] hashCode = HASH.hashBytes(bytes).asBytes();
    long lowBytes = readUnsignedLong(hashCode, 0, 8);
    long highBytes = readUnsignedLong(hashCode, 8, 8);
    return new Address(highBytes, normalizeLowBytes(lowBytes));
  }

  @TestOnly
  public void store(@NotNull Address address, @NotNull byte[] bytes) {
    myClient[0].set(address.toString(), 0, bytes, MY_TRANSCODER);
  }

  public void disablePrefetch() {
    prefetchInProgress.set(Integer.MAX_VALUE);
  }

  @Override
  public void prefetch(@NotNull Address prefetchAddress, @NotNull byte[] bytes, @NotNull BTree tree, int size, byte type, int mask) {
    if (prefetchInProgress.get() > 70) {
      return;
    }

    if (type == BTree.BOTTOM) {
      if (mask == ~(0xFFFFFFFF << size)) { // all bits set to 1
        return;
      }
    }

    if (preFetches.putIfAbsent(prefetchAddress, bytes) != null) {
      return;
    }

    final int bytesPerKey = tree.getKeySize();
    final List<String> addresses = new ArrayList<>(size);
    final List<Address> addressValues = new ArrayList<>(size);

    for (int i = 0; i < size; i++) {
      if ((mask & (1L << i)) == 0) {
        final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * i + bytesPerKey;
        final long lowBytes = readUnsignedLong(bytes, offset, 8);
        final long highBytes = readUnsignedLong(bytes, offset + 8, 8);
        Address address = new Address(highBytes, lowBytes);
        if (myLocalCache.getIfPresent(address) == null) { // don't prefetch already cached stuff
          addresses.add(address.toString());
          addressValues.add(address);
        }
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
    prefetchInProgress.incrementAndGet();
    final BulkFuture<Map<String, byte[]>> future = myClient[index].asyncGetBulkBytes(addresses.iterator());
    future.addListener(new BulkGetCompletionListener() {
      @Override
      public void onComplete(BulkGetFuture<?> future) throws Exception {
        prefetchInProgress.decrementAndGet();
        preFetches.remove(prefetchAddress);
        Map<String, ?> data = future.get();
        if (future.getStatus().isSuccess()) {
          data.forEach((key, value) -> {
            final int index = addresses.indexOf(key);
            if (index >= 0) {
              myLocalCache.put(addressValues.get(index), value);
            }
          });
        }
        else {
          addressValues.forEach(address -> myLocalCache.invalidate(address)); // cleanup futures
        }
      }
    });
    addressValues.forEach(address -> myLocalCache.put(address, future));
  }

  @Override
  public void bulkLookup(@NotNull List<Address> addresses, @NotNull DataConsumer consumer) {
    final int size = addresses.size();
    final Map<String, Address> serializedAddresses = new HashMap<>(size);
    final List<String> loadAddresses = new ArrayList<>(size);

    addresses.forEach(address -> {
      String loadAddress = address.toString();
      if (serializedAddresses.put(loadAddress, address) != null) {
        throw new IllegalStateException("address in not unique: " + address);
      }
      loadAddresses.add(loadAddress);
    });

    int clientIndex = this.q + 1;
    if (clientIndex >= CLIENT_COUNT) {
      clientIndex = 0;
    }
    q = clientIndex;

    final BulkFuture<Map<String, byte[]>> future = myClient[clientIndex].asyncGetBulkBytes(loadAddresses.iterator());
    final Map<String, byte[]> data;
    try {
      data = future.get();
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    if (future.getStatus().isSuccess()) {
      data.forEach((key, value) -> {
        final Address address = serializedAddresses.remove(key);
        if (address != null) {
          myLocalCache.put(address, value);
          consumer.consume(address, value);
        }
        else {
          throw new IllegalStateException("weird address");
        }
      });
      if (!serializedAddresses.isEmpty()) {
        throw new IllegalStateException("not all addresses returned");
      }
    }
    else {
      throw new RuntimeException("future failed");
    }
  }

  @Override
  @SuppressWarnings("WaitNotInLoop")
  public Address bulkStore(@NotNull Tree tree, @NotNull Novelty.Accessor novelty) {
    final int packSize = 10000;
    final List<Pair<Address, byte[]>> data = new ArrayList<>(packSize);
    final Set<Future> packs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    final StorageConsumer consumer = new StorageConsumer() {
      int client = 0;

      @Override
      public void store(@NotNull Address address, @NotNull byte[] bytes) {
        // if (!address.equals(alloc(bytes))) { throw new IllegalStateException(); }
        data.add(new Pair<>(address, bytes));
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

  public void dumpStats(@NotNull final PrintStream stream) {
    final CacheStats stats = myLocalCache.stats();
    if (stats != null) {
      stream.println("direct hits: " + stats.hitCount() + ", misses: " + stats.missCount() + ", evictions: " + stats.evictionCount());
      stream.println("prefetches in progress: " + prefetchInProgress.get());
      stream.println("prefetch hits: " + prefetchHits.get());
    }
  }

  public void close() {
    dumpStats(System.out);
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

  @NotNull
  private static HackMemcachedClient[] createClients(InetSocketAddress socketAddress) throws IOException {
    HackMemcachedClient[] client = new HackMemcachedClient[CLIENT_COUNT];
    final List<InetSocketAddress> address = Collections.singletonList(socketAddress);
    for (int i = 0; i < CLIENT_COUNT; i++) {
      client[i] = new HackMemcachedClient(new BinaryConnectionFactory(ClientMode.Static, 16384, 16384 * 8), address);
    }
    return client;
  }

  private static final class HackMemcachedClient extends MemcachedClient {

    public HackMemcachedClient(ConnectionFactory cf, List<InetSocketAddress> addrs) throws IOException {
      super(cf, addrs);
    }

    public BulkFuture<Map<String, byte[]>> asyncGetBulkBytes(Iterator<String> keyIter) {
      final Map<String, Future<byte[]>> m = new ConcurrentHashMap<>();

      // Break the gets down into groups by key
      final Map<MemcachedNode, List<String>> chunks = new HashMap<>();
      final NodeLocator locator = mconn.getLocator();

      while (keyIter.hasNext()) {
        String key = keyIter.next();
        StringUtils.validateKey(key, true);
        final MemcachedNode primaryNode = locator.getPrimary(key);
        MemcachedNode node = null;
        if (primaryNode.isActive()) {
          node = primaryNode;
        }
        else {
          for (Iterator<MemcachedNode> i = locator.getSequence(key); node == null && i.hasNext(); ) {
            MemcachedNode n = i.next();
            if (n.isActive()) {
              node = n;
            }
          }
          if (node == null) {
            node = primaryNode;
          }
        }
        List<String> ks = chunks.get(node);
        if (ks == null) {
          ks = new ArrayList<>();
          chunks.put(node, ks);
        }
        ks.add(key);
      }

      final AtomicInteger pendingChunks = new AtomicInteger(chunks.size());
      int initialLatchCount = chunks.isEmpty() ? 0 : 1;
      final CountDownLatch latch = new CountDownLatch(initialLatchCount);
      final Collection<Operation> ops = new ArrayList<>(chunks.size());
      final BulkGetFuture<byte[]> rv = new BulkGetFuture<>(m, ops, latch, executorService);

      GetOperation.Callback cb = new GetOperation.Callback() {
        @Override
        @SuppressWarnings("synthetic-access")
        public void receivedStatus(OperationStatus status) {
          if (status.getStatusCode() == StatusCode.ERR_NOT_MY_VBUCKET) {
            pendingChunks.addAndGet(Integer.parseInt(status.getMessage()));
          }
          rv.setStatus(status);
        }

        @Override
        public void gotData(String k, int flags, byte[] data) {
          m.put(k, tcService.decode(MY_TRANSCODER, new CachedData(flags, data, MY_TRANSCODER.getMaxSize())));
        }

        @Override
        public void complete() {
          if (pendingChunks.decrementAndGet() <= 0) {
            latch.countDown();
            rv.signalComplete();
          }
        }
      };

      // Now that we know how many servers it breaks down into, and the latch
      // is all set up, convert all of these strings collections to operations
      final Map<MemcachedNode, Operation> mops = new HashMap<>();

      for (Map.Entry<MemcachedNode, List<String>> me : chunks.entrySet()) {
        Operation op = new MultiGetOperationFastImpl(me.getValue(), cb);
        mops.put(me.getKey(), op);
        ops.add(op);
      }
      assert mops.size() == chunks.size();
      // mconn.checkState();
      mconn.addOperations(mops);
      return rv;
    }
  }

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
}
