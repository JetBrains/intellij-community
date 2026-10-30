// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.diagnostic.Logger;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.function.Predicate;

/// Array-backed queue with additional support for fast bulk inserts (see [bulkEnqueueFirst])
@ApiStatus.Internal
public final class BulkArrayQueue<T> {
  private static final Logger LOG = Logger.getInstance(BulkArrayQueue.class);

  // maintain wrap-around queue using these pointers into myQueue
  private int tail; // index at which the next element would be stored via enqueue()
  private int head; // index to a stored element to be returned by pollFirst(); if tail==head the queue is empty
  private Object[] myQueue;

  public BulkArrayQueue(int initialCapacity) {
    if (initialCapacity <= 0) throw new IllegalArgumentException("initialCapacity must be positive: " + initialCapacity);
    myQueue = new Object[initialCapacity];
  }

  public void addLast(@NotNull T info) {
    int newTail = wrapAroundIndexForward(tail + 1);
    if (newTail == head) {
      growAndUnwrap(0);
      newTail = tail+1;
    }
    myQueue[tail] = info;
    tail = newTail;
  }

  public void addFirst(@NotNull T info) {
    int newHead = wrapAroundIndexBackward(head-1);
    if (newHead == tail) {
      growAndUnwrap(1);
      newHead = 0;
    }
    myQueue[newHead] = info;
    head = newHead;
  }

  // grow the backing array to accommodate additional elements;
  // convert wrapped-around pointers to the conventional head<tail in the process
  // and allocate "reserveAtStart" empty slots before "head" to prepare for bulk insert
  private void growAndUnwrap(int reserveAtStart) {
    if (reserveAtStart < 0) throw new IllegalArgumentException("illegal argument: "+reserveAtStart);
    int oldCapacity = myQueue.length;
    int newCapacity = reserveAtStart + (oldCapacity < 100_000 ? oldCapacity * 2 : oldCapacity + (oldCapacity >> 1)); // overflow aware
    if (newCapacity <= oldCapacity + reserveAtStart) {
      throw new OutOfMemoryError("reserveAtStart: "+reserveAtStart+"; oldCapacity: "+oldCapacity);
    }
    Object[] newQueue = new Object[newCapacity];
    int firstChunkSize;
    if (head <= tail) {
      firstChunkSize = tail - head;
    }
    else {
      firstChunkSize = oldCapacity - head;
      System.arraycopy(myQueue, 0, newQueue, reserveAtStart + firstChunkSize, tail);
    }
    try {
      System.arraycopy(myQueue, head, newQueue, reserveAtStart, firstChunkSize);
    } catch (Throwable e) {
      LOG.warn("Could not copy array: head=" + head + ", tail=" + tail + ", capacity=" + oldCapacity + ", reserveAtStart=" + reserveAtStart + ", firstChunkSize=" + firstChunkSize, e);
      throw e;
    }
    tail = size() + reserveAtStart;
    head = reserveAtStart;
    myQueue = newQueue;
  }


  public int size() {
    return head <= tail ? tail - head : tail + myQueue.length-head;
  }

  public int capacity() { return myQueue.length; }

  public T pollFirst() {
    if (isEmpty()) return null;
    int nextHead = wrapAroundIndexForward(head + 1);
    T info = getAndNullize(head);
    head = nextHead;
    return info;
  }

  /**
   * Removes and returns the last element.
   * @throws NoSuchElementException if the queue is empty
   */
  public @NotNull T removeLast() {
    if (isEmpty()) throw new NoSuchElementException();
    tail = wrapAroundIndexBackward(tail-1);
    return getAndNullize(tail);
  }

  /// Returns the element at the given `logicalIndex`.
  /// `logicalIndex==0` means the queue head, while `logicalIndex==size()-1` means the queue tail element.
  /// @throws IndexOutOfBoundsException if `logicalIndex < 0 || logicalIndex >= size()`
  public @NotNull T elementAt(int logicalIndex) {
    if (logicalIndex < 0 || logicalIndex >= size()) {
      throw new IndexOutOfBoundsException("index: " + logicalIndex + "; size: " + size());
    }
    int offsetToEnd = myQueue.length - head;
    //noinspection unchecked
    return (T)myQueue[logicalIndex < offsetToEnd ? head + logicalIndex : logicalIndex - offsetToEnd];
  }
  /// Returns the element at the given `logicalIndex`.
  /// `logicalIndex==0` means the queue head, while `logicalIndex==size()-1` means the queue tail element,
  /// and then set that element to `tombStone`
  /// @throws IndexOutOfBoundsException if `logicalIndex < 0 || logicalIndex >= size()`
  public @NotNull T getAndSetElementAt(int logicalIndex, @NotNull T tombStone) {
    if (logicalIndex < 0 || logicalIndex >= size()) {
      throw new IndexOutOfBoundsException("index: " + logicalIndex + "; size: " + size());
    }
    int offsetToEnd = myQueue.length - head;
    int i = logicalIndex < offsetToEnd ? head + logicalIndex : logicalIndex - offsetToEnd;
    //noinspection unchecked
    T result = (T)myQueue[i];
    myQueue[i] = tombStone;
    return result;
  }

  public T peekFirst() {
    if (isEmpty()) return null;
    //noinspection unchecked
    return (T)myQueue[head];
  }

  public T peekLast() {
    if (isEmpty()) return null;
    int lastIndex = wrapAroundIndexBackward(tail-1);
    //noinspection unchecked
    return (T)myQueue[lastIndex];
  }

  private @NotNull T getAndNullize(int head) {
    //noinspection unchecked
    T t = (T)myQueue[head];
    myQueue[head] = null;
    return t;
  }

  // insert all from "elements" before "head"
  public void bulkEnqueueFirst(@NotNull ObjectArrayList<? extends @NotNull T> elements) {
    int insertSize = elements.size();
    int oldCapacity = myQueue.length;
    int emptySpace = oldCapacity - size() - 1;
    if (insertSize > emptySpace) {
      growAndUnwrap(insertSize);
    }
    int secondChunkSize = head <= tail ? Math.min(insertSize, head) : Math.min(insertSize, head - tail - 1);
    int firstChunkSize = insertSize - secondChunkSize;

    head -= secondChunkSize;
    elements.getElements(firstChunkSize, myQueue, head, secondChunkSize);

    if (firstChunkSize != 0) {
      head = oldCapacity - firstChunkSize;
      elements.getElements(0, myQueue, head, firstChunkSize);
    }
  }

  public void removeAll(@NotNull Predicate<? super T> shouldRemove) {
    int o;
    if (head <= tail) {
      // shift alive items in [head...tail) left to head
      o = head;
      for (int i=head; i<tail; i++) {
        T info = getAndNullize(i);
        if (!shouldRemove.test(info)) {
          myQueue[o++] = info;
        }
      }
    }
    else {
      // shift living items in [head...capacity) right
      o = myQueue.length;
      for (int i= myQueue.length-1; i>=head; i--) {
        T info = getAndNullize(i);
        if (!shouldRemove.test(info)) {
          myQueue[--o] = info;
        }
      }
      head = wrapAroundIndexForward(o);
      // shift alive items in [0...tail) left to 0
      o = 0;
      for (int i=0; i<tail; i++) {
        T info = getAndNullize(i);
        if (!shouldRemove.test(info)) {
          myQueue[o++] = info;
        }
      }
    }
    tail = o;
  }

  private int wrapAroundIndexForward(int i) {
    return i == myQueue.length ? 0 : i;
  }
  private int wrapAroundIndexBackward(int i) {
    return i == -1 ? myQueue.length - 1 : i;
  }

  public boolean isEmpty() {
    return head == tail;
  }
}
