package com.siyeh.igtest.threading.while_loop_spins_on_field;

import java.util.concurrent.locks.*;

/**
 * ConditionBoundedBuffer
 * <p/>
 * Bounded buffer using explicit condition variables
 *
 * @author Brian Goetz and Tim Peierls
 */
public class ConditionBoundedBuffer <T> {
  protected final Lock lock = new ReentrantLock();
  // CONDITION PREDICATE: notFull (count < items.length)
  private final Condition notFull = lock.newCondition();
  // CONDITION PREDICATE: notEmpty (count > 0)
  private final Condition notEmpty = lock.newCondition();
  private static final int BUFFER_SIZE = 100;
  private final T[] items = (T[]) new Object[BUFFER_SIZE];
  private int tail, head, count;

  // BLOCKS-UNTIL: notFull
  public void put(T x) throws InterruptedException {
    lock.lock();
    try {
      while (count == items.length)
        notFull.await();
      items[tail] = x;
      if (++tail == items.length)
        tail = 0;
      ++count;
      notEmpty.signal();
    } finally {
      lock.unlock();
    }
  }

  // BLOCKS-UNTIL: notEmpty
  public T take() throws InterruptedException {
    lock.lock();
    try {
      while (count == 0)
        notEmpty.await();
      T x = items[head];
      items[head] = null;
      if (++head == items.length)
        head = 0;
      --count;
      notFull.signal();
      return x;
    } finally {
      lock.unlock();
    }
  }
}