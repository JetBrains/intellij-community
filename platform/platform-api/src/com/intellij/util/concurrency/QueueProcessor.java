// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * <p>QueueProcessor processes elements which are being added to a queue via {@link #add(Object)} and {@link #addFirst(Object)} methods.</p>
 * <p>Elements are processed one by one in a special single thread.
 * The processor itself is passed in the constructor and is called from that thread.
 * By default processing starts when the first element is added to the queue, though there is an 'autostart' option which holds
 * the processor until {@link #start()} is called.</p>
 * This class is thread-safe.
 * @param <T> type of queue elements.
 */
public class QueueProcessor<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.QueueProcessor");
  public enum ThreadToUse {
    AWT,
    POOLED
  }

  private final PairConsumer<T, Runnable> myProcessor;
  private final Deque<T> myQueue = new ArrayDeque<>();

  private boolean isProcessing;
  private boolean myStarted;

  private final ThreadToUse myThreadToUse;
  private final Condition<?> myDeathCondition;
  private final Map<Object, ModalityState> myModalityState = ContainerUtil.newIdentityTroveMap();

  /**
   * Constructs a QueueProcessor, which will autostart as soon as the first element is added to it.
   */
  public QueueProcessor(@NotNull Consumer<T> processor) {
    this(processor, Conditions.alwaysFalse());
  }

  /**
   * Constructs a QueueProcessor, which will autostart as soon as the first element is added to it.
   */
  public QueueProcessor(@NotNull Consumer<T> processor, @NotNull Condition<?> deathCondition) {
    this(processor, deathCondition, true);
  }

  public QueueProcessor(@NotNull Consumer<T> processor, @NotNull Condition<?> deathCondition, boolean autostart) {
    this(wrappingProcessor(processor), autostart, ThreadToUse.POOLED, deathCondition);
  }

  @NotNull
  public static QueueProcessor<Runnable> createRunnableQueueProcessor() {
    return new QueueProcessor<>(new RunnableConsumer());
  }

  @NotNull
  public static QueueProcessor<Runnable> createRunnableQueueProcessor(ThreadToUse threadToUse) {
    return new QueueProcessor<>(wrappingProcessor(new RunnableConsumer()), true, threadToUse, Conditions.FALSE);
  }

  @NotNull
  private static <T> PairConsumer<T, Runnable> wrappingProcessor(@NotNull final Consumer<T> processor) {
    return (item, continuation) -> {
      // try-with-resources is the most simple way to ensure no suppressed exception is lost
      try (SilentAutoClosable ignored = continuation::run) {
        runSafely(() -> processor.consume(item));
      }
    };
  }

  /**
   * Constructs a QueueProcessor with the given processor and autostart setting.
   * By default QueueProcessor starts processing when it receives the first element. Pass {@code false} to alternate its behavior.
   *
   * @param processor processor of queue elements.
   * @param autostart if {@code true} (which is by default), the queue will be processed immediately when it receives the first element.
   *                  If {@code false}, then it will wait for the {@link #start()} command.
   *                  After QueueProcessor has started once, autostart setting doesn't matter anymore: all other elements will be processed immediately.
   */

  public QueueProcessor(@NotNull PairConsumer<T, Runnable> processor,
                        boolean autostart,
                        @NotNull ThreadToUse threadToUse,
                        @NotNull Condition<?> deathCondition) {
    myProcessor = processor;
    myStarted = autostart;
    myThreadToUse = threadToUse;
    myDeathCondition = deathCondition;
  }

  /**
   * Starts queue processing if it hasn't started yet.
   * Effective only if the QueueProcessor was created with no-autostart option: otherwise processing will start as soon as the first element
   * is added to the queue.
   * If there are several elements in the queue, processing starts from the first one.
   */
  public void start() {
    synchronized (myQueue) {
      if (myStarted) return;
      myStarted = true;
      if (!myQueue.isEmpty()) {
        startProcessing();
      }
    }
  }
  
  private void finishProcessing(boolean continueProcessing) {
    synchronized (myQueue) {
      isProcessing = false;
      if (myQueue.isEmpty()) {
        myQueue.notifyAll();
      }
      else if (continueProcessing){
        startProcessing();
      }
    }
  }

  public void add(@NotNull T t, ModalityState state) {
    synchronized (myQueue) {
      myModalityState.put(t, state);
    }
    doAdd(t, false);
  }

  public void add(@NotNull T element) {
    doAdd(element, false);
  }

  public void addFirst(@NotNull T element) {
    doAdd(element, true);
  }

  private void doAdd(@NotNull T element, boolean atHead) {
    synchronized (myQueue) {
      if (atHead) {
        myQueue.addFirst(element);
      }
      else {
        myQueue.add(element);
      }
      startProcessing();
    }
  }

  public void clear() {
    synchronized (myQueue) {
      myQueue.clear();
    }
  }

  public void waitFor() {
    synchronized (myQueue) {
      while (isProcessing) {
        try {
          myQueue.wait();
        }
        catch (InterruptedException e) {
          //ok
        }
      }
    }
  }
  
  boolean waitFor(long timeoutMS) {
    synchronized (myQueue) {
      long start = System.currentTimeMillis();
      
      while (isProcessing) {
        long rest = timeoutMS - (System.currentTimeMillis() - start);
        
        if (rest <= 0) return !isProcessing;
        
        try {
          myQueue.wait(rest);
        }
        catch (InterruptedException e) {
          //ok
        }
      }
      
      return true;
    }
  }

  private boolean startProcessing() {
    LOG.assertTrue(Thread.holdsLock(myQueue));

    if (isProcessing || !myStarted) {
      return false;
    }
    isProcessing = true;
    final T item = myQueue.removeFirst();
    final Runnable runnable = () -> {
      if (myDeathCondition.value(null)) {
        finishProcessing(false);
        return;
      }
      runSafely(() -> myProcessor.consume(item, () -> finishProcessing(true)));
    };
    final Application application = ApplicationManager.getApplication();
    if (myThreadToUse == ThreadToUse.AWT) {
      final ModalityState state = myModalityState.remove(item);
      if (state != null) {
        application.invokeLater(runnable, state);
      }
      else {
        application.invokeLater(runnable);
      }
    }
    else {
      application.executeOnPooledThread(runnable);
    }
    return true;
  }

  public static void runSafely(@NotNull Runnable run) {
    try {
      run.run();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      try {
        LOG.error(e);
      }
      catch (Throwable e2) {
        //noinspection CallToPrintStackTrace
        e2.printStackTrace();
      }
    }
  }

  public boolean isEmpty() {
    synchronized (myQueue) {
      return myQueue.isEmpty() && !isProcessing;
    }
  }

  /**
   * Removes several last tasks in the queue, leaving only {@code remaining} amount of them, counted from the head of the queue.
   */
  public void dismissLastTasks(int remaining) {
    synchronized (myQueue) {
      while (myQueue.size() > remaining) {
        myQueue.pollLast();
      }
    }
  }

  public boolean hasPendingItemsToProcess() {
    synchronized (myQueue) {
      return !myQueue.isEmpty();
    }
  }

  @FunctionalInterface
  protected interface SilentAutoClosable extends AutoCloseable {
    @Override
    void close();
  }

  public static final class RunnableConsumer implements Consumer<Runnable> {
    @Override
    public void consume(Runnable runnable) {
      runnable.run();
    }
  }
}
