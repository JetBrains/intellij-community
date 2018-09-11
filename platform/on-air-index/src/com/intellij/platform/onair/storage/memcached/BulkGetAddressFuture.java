// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage.memcached;

import com.intellij.platform.onair.storage.api.Address;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.compat.log.LoggerFactory;
import net.spy.memcached.internal.*;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationState;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.ops.StatusCode;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.*;

public class BulkGetAddressFuture<T>
  extends AbstractListenableFuture<Map<Address, T>, BulkGetAddressCompletionListener>
  implements BulkFuture<Map<Address, T>> {

  private final Map<Address, Future<T>> rvMap;
  private final Collection<Operation> ops;
  private final CountDownLatch latch;
  private OperationStatus status;
  private boolean cancelled = false;
  private boolean timeout = false;

  public BulkGetAddressFuture(Map<Address, Future<T>> m, Collection<Operation> getOps,
                              CountDownLatch l, ExecutorService service) {
    super(service);
    rvMap = m;
    ops = getOps;
    latch = l;
    status = null;
  }

  public boolean cancel(boolean ign) {
    boolean rv = false;
    for (Operation op : ops) {
      rv |= op.getState() == OperationState.WRITE_QUEUED;
      op.cancel();
    }
    for (Future<T> v : rvMap.values()) {
      v.cancel(ign);
    }
    cancelled = true;
    status = new OperationStatus(false, "Cancelled", StatusCode.CANCELLED);
    notifyListeners();
    return rv;
  }

  public Map<Address, T> get() throws InterruptedException, ExecutionException {
    try {
      return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }
    catch (TimeoutException e) {
      throw new RuntimeException("Timed out waiting forever", e);
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see net.spy.memcached.internal.BulkFuture#getSome(long,
   * java.util.concurrent.TimeUnit)
   */
  public Map<Address, T> getSome(long to, TimeUnit unit)
    throws InterruptedException, ExecutionException {
    Collection<Operation> timedoutOps = new HashSet<>();
    Map<Address, T> ret = internalGet(to, unit, timedoutOps);
    if (timedoutOps.size() > 0) {
      timeout = true;
      LoggerFactory.getLogger(getClass()).warn(
        new CheckedOperationTimeoutException("Operation timed out: ",
                                             timedoutOps).getMessage());
    }
    return ret;
  }

  /*
   * get all or nothing: timeout exception is thrown if all the data could not
   * be retrieved
   *
   * @see java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)
   */
  public Map<Address, T> get(long to, TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
    Collection<Operation> timedoutOps = new HashSet<>();
    Map<Address, T> ret = internalGet(to, unit, timedoutOps);
    if (timedoutOps.size() > 0) {
      this.timeout = true;
      throw new CheckedOperationTimeoutException("Operation timed out.",
                                                 timedoutOps);
    }
    return ret;
  }

  /**
   * refactored code common to both get(long, TimeUnit) and getSome(long,
   * TimeUnit).
   *
   * @param to
   * @param unit
   * @param timedOutOps
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   */
  private Map<Address, T> internalGet(long to, TimeUnit unit,
                                      Collection<Operation> timedOutOps) throws InterruptedException,
                                                                                ExecutionException {
    if (!latch.await(to, unit)) {
      for (Operation op : ops) {
        if (op.getState() != OperationState.COMPLETE) {
          MemcachedConnection.opTimedOut(op);
          timedOutOps.add(op);
        }
        else {
          MemcachedConnection.opSucceeded(op);
        }
      }
    }
    for (Operation op : ops) {
      if (op.isCancelled()) {
        throw new ExecutionException(new CancellationException("Cancelled"));
      }
      if (op.hasErrored()) {
        throw new ExecutionException(op.getException());
      }
    }
    Map<Address, T> m = new HashMap<>();
    for (Map.Entry<Address, Future<T>> me : rvMap.entrySet()) {
      m.put(me.getKey(), me.getValue().get());
    }
    return m;
  }

  public OperationStatus getStatus() {
    if (status == null) {
      try {
        get();
      }
      catch (InterruptedException e) {
        status = new OperationStatus(false, "Interrupted", StatusCode.INTERRUPTED);
        Thread.currentThread().interrupt();
      }
      catch (ExecutionException e) {
        return status;
      }
    }
    return status;
  }

  public void setStatus(OperationStatus s) {
    status = s;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public boolean isDone() {
    return latch.getCount() == 0;
  }

  /*
   * set to true if timeout was reached.
   *
   * @see net.spy.memcached.internal.BulkFuture#isTimeout()
   */
  public boolean isTimeout() {
    return timeout;
  }

  @Override
  public Future<Map<Address, T>> addListener(
    BulkGetAddressCompletionListener listener) {
    super.addToListeners((GenericCompletionListener)listener);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Future<Map<Address, T>> removeListener(
    BulkGetAddressCompletionListener listener) {
    super.removeFromListeners((GenericCompletionListener)listener);
    return this;
  }

  @Override
  public Future<Map<Address, T>> addListener(BulkGetCompletionListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Map<Address, T>> removeListener(BulkGetCompletionListener listener) {
    throw new UnsupportedOperationException();
  }

  /**
   * Signals that this future is complete.
   */
  public void signalComplete() {
    notifyListeners();
  }
}
