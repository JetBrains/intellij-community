/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author peter
 */
public class TransactionGuardImpl extends TransactionGuard {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.TransactionGuardImpl");
  private final Queue<Transaction> myQueue = new LinkedBlockingQueue<Transaction>();
  private final Set<TransactionKind> myMergeableKinds = ContainerUtil.newHashSet();
  private final Map<ProgressIndicator, TransactionIdImpl> myProgresses = ContainerUtil.createConcurrentWeakMap();
  private TransactionIdImpl myCurrentTransaction;
  private boolean myWritingAllowed;

  @Override
  @NotNull
  public AccessToken startSynchronousTransaction(@NotNull TransactionKind kind) throws IllegalStateException {
    if (isInsideTransaction() && !myWritingAllowed && !myMergeableKinds.contains(kind)) {
      // please assign exceptions that occur here to Peter
      LOG.error("Synchronous transactions are allowed only from user actions. " +
                "Please use submit*Transaction instead of invokeLater. " +
                "See FAQ in TransactionGuard class javadoc.");
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    return startTransactionUnchecked();
  }

  @NotNull
  private AccessToken startTransactionUnchecked() {
    final TransactionIdImpl prevTransaction = myCurrentTransaction;
    final boolean wasWritingAllowed = myWritingAllowed;

    myWritingAllowed = true;
    myCurrentTransaction = new TransactionIdImpl();

    return new AccessToken() {
      @Override
      public void finish() {
        Queue<Transaction> queue = getQueue(prevTransaction);
        queue.addAll(myCurrentTransaction.myQueue);
        if (!queue.isEmpty()) {
          pollQueueLater();
        }

        myWritingAllowed = wasWritingAllowed;
        myCurrentTransaction = prevTransaction;
      }
    };
  }

  @NotNull
  private Queue<Transaction> getQueue(@Nullable TransactionIdImpl prevTransaction) {
    return prevTransaction == null ? myQueue : prevTransaction.myQueue;
  }

  private void pollQueueLater() {
    //todo replace with SwingUtilities when write actions are required to run under a guard
    final Application app = ApplicationManager.getApplication();
    app.invokeLater(new Runnable() {
      @Override
      public void run() {
        Queue<Transaction> queue = getQueue(myCurrentTransaction);
        Transaction next = queue.peek();
        if (next != null && canRunTransactionNow(next, false)) {
          queue.remove();
          runSyncTransaction(next);
        }
      }
    }, app.getDisposed());
  }

  private void runSyncTransaction(@NotNull Transaction transaction) {
    AccessToken token = startTransactionUnchecked();
    try {
      if (!Disposer.isDisposed(transaction.parentDisposable)) {
        transaction.runnable.run();
      }
    }
    finally {
      token.finish();
    }
  }

  private boolean isInsideTransaction() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myCurrentTransaction != null;
  }

  @Override
  public void submitMergeableTransaction(@NotNull final Disposable parentDisposable, @NotNull final TransactionKind kind, @NotNull final Runnable _transaction) {
    submitMergeableTransaction(parentDisposable, kind, getCurrentMergeableTransaction(), _transaction);
  }

  @Override
  public void submitMergeableTransaction(@NotNull Disposable parentDisposable, @Nullable TransactionId mergeInto, @NotNull Runnable transaction) {
    submitMergeableTransaction(parentDisposable, TransactionKind.ANY_CHANGE, (TransactionIdImpl)mergeInto, transaction);
  }

  private void submitMergeableTransaction(@NotNull final Disposable parentDisposable,
                                          @NotNull final TransactionKind kind,
                                          @Nullable final TransactionIdImpl expectedId,
                                          @NotNull final Runnable _transaction) {
    @NotNull final Transaction transaction = new Transaction(_transaction, expectedId, kind, parentDisposable);
    final Application app = ApplicationManager.getApplication();
    final boolean isDispatchThread = app.isDispatchThread();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (canRunTransactionNow(transaction, isDispatchThread)) {
          runSyncTransaction(transaction);
        }
        else {
          getQueue(expectedId).offer(transaction);
          pollQueueLater();
        }
      }
    };

    if (isDispatchThread) {
      runnable.run();
    } else {
      //todo add ModalityState.any() when write actions are required to run under a guard
      app.invokeLater(runnable, app.getDisposed());
    }
  }

  private boolean canRunTransactionNow(Transaction transaction, boolean sync) {
    TransactionIdImpl currentId = myCurrentTransaction;
    if (currentId == null || myMergeableKinds.contains(transaction.kind)) {
      return true;
    }

    if (sync && !myWritingAllowed) {
      return false;
    }

    return transaction.mergeInto != null && currentId.myStartCounter <= transaction.mergeInto.myStartCounter;
  }

  @Override
  @NotNull
  public AccessToken acceptNestedTransactions(@NotNull TransactionKind... kinds) {
    //todo enable when transactions are mandatory
    /*
    if (!isInsideTransaction()) {
      throw new IllegalStateException("acceptNestedTransactions must be called inside a transaction");
    }
    */
    final List<TransactionKind> toRemove = ContainerUtil.newArrayList();
    for (TransactionKind kind : kinds) {
      if (myMergeableKinds.add(kind)) {
        toRemove.add(kind);
      }
    }
    return new AccessToken() {
      @Override
      public void finish() {
        myMergeableKinds.removeAll(toRemove);
      }
    };
  }

  @Override
  public void assertInsideTransaction(boolean transactionRequired, @NotNull String errorMessage) {
    if (transactionRequired != isInsideTransaction()) {
      LOG.error(errorMessage);
    }
  }

  @Override
  public void submitTransactionAndWait(@NotNull TransactionKind kind, @NotNull final Runnable runnable) throws ProcessCanceledException {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      Transaction transaction = new Transaction(runnable, getCurrentMergeableTransaction(), kind, app);
      if (!canRunTransactionNow(transaction, true)) {
        throw new AssertionError("Cannot run synchronous submitTransactionAndWait from invokeLater. " +
                                 "Please use asynchronous submit*Transaction. " +
                                 "See TransactionGuard FAQ for details.");
      }
      runSyncTransaction(transaction);
      return;
    }

    assert !app.isReadAccessAllowed() : "submitTransactionAndWait should not be invoked from a read action";
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Throwable[] exception = {null};
    submitMergeableTransaction(ApplicationManager.getApplication(), kind, new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        }
        catch (Throwable e) {
          exception[0] = e;
        }
        finally {
          semaphore.up();
        }
      }
    });
    semaphore.waitFor();
    if (exception[0] != null) {
      throw new RuntimeException(exception[0]);
    }
  }

  /**
   * An absolutely guru method!<p/>
   *
   * Executes the given code and marks it as a user activity, to allow write actions to be run without requiring transactions.
   * This is only to be called from UI infrastructure, during InputEvent processing and wrap the point where the control
   * goes to custom input event handlers for the first time.<p/>
   *
   * If you wish to invoke some actionPerformed,
   * please consider using {@code ActionManager.tryToExecute()} instead, or ensure in some other way that the action is enabled
   * and can be invoked in the current modality state.
   */
  public void performUserActivity(Runnable activity) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    AccessToken token = startActivity(true);
    try {
      activity.run();
    }
    finally {
      token.finish();
    }
  }

  /**
   * An absolutely guru method, only intended to be used from Swing event processing. Please consult Peter if you think you need to invoke this.
   */
  @NotNull
  public AccessToken startActivity(boolean userActivity) {
    if (myWritingAllowed == userActivity) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    final boolean prev = myWritingAllowed;
    myWritingAllowed = userActivity;
    return new AccessToken() {
      @Override
      public void finish() {
        myWritingAllowed = prev;
      }
    };
  }

  public boolean isWriteActionAllowed() {
    return !Registry.is("ide.require.transaction.for.model.changes", false) || isInsideTransaction() || myWritingAllowed;
  }

  @Override
  public void submitTransactionLater(@NotNull final Disposable parentDisposable, @NotNull final Runnable transaction) {
    final TransactionIdImpl id = getCurrentMergeableTransaction();
    Application app = ApplicationManager.getApplication();
    app.invokeLater(new Runnable() {
      @Override
      public void run() {
        submitMergeableTransaction(parentDisposable, id, transaction);
      }
    }, app.getDisposed());
  }

  @Override
  public TransactionIdImpl getCurrentMergeableTransaction() {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      return indicator != null ? myProgresses.get(indicator) : null;
    }

    return myWritingAllowed ? myCurrentTransaction : null;
  }

  public void registerProgress(@NotNull ProgressIndicator indicator, @Nullable TransactionIdImpl contextTransaction) {
    if (contextTransaction != null) {
      myProgresses.put(indicator, contextTransaction);
    }
  }

  private static class Transaction {
    @NotNull  final Runnable runnable;
    @Nullable final TransactionIdImpl mergeInto;
    @NotNull  final TransactionKind kind;
    @NotNull  final Disposable parentDisposable;

    Transaction(@NotNull Runnable runnable,
                       @Nullable TransactionIdImpl mergeInto,
                       @NotNull TransactionKind kind,
                       @NotNull Disposable parentDisposable) {
      this.runnable = runnable;
      this.mergeInto = mergeInto;
      this.kind = kind;
      this.parentDisposable = parentDisposable;
    }
  }

  private static class TransactionIdImpl implements TransactionId {
    private static final AtomicLong ourTransactionCounter = new AtomicLong();
    final long myStartCounter = ourTransactionCounter.getAndIncrement();
    final Queue<Transaction> myQueue = new LinkedBlockingQueue<Transaction>();

    @Override
    public String toString() {
      return "Transaction " + myStartCounter;
    }
  }
}
