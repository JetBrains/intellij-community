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

import com.google.common.base.MoreObjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author peter
 */
public class TransactionGuardImpl extends TransactionGuard {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.TransactionGuardImpl");
  private final Queue<Transaction> myQueue = new LinkedBlockingQueue<>();
  private final Map<ModalityState, TransactionIdImpl> myModality2Transaction = ContainerUtil.createConcurrentWeakMap();

  /**
   * Remembers the value of {@link #myWritingAllowed} at the start of each modality. If writing wasn't allowed at that moment
   * (e.g. inside SwingUtilities.invokeLater), it won't be allowed for all dialogs inside such modality, even from user activity.
   */
  private final Map<ModalityState, Boolean> myWriteSafeModalities = ContainerUtil.createConcurrentWeakMap();
  private TransactionIdImpl myCurrentTransaction;
  private boolean myWritingAllowed;
  private boolean myErrorReported;

  public TransactionGuardImpl() {
    myWriteSafeModalities.put(ModalityState.NON_MODAL, true);
    myWritingAllowed = SwingUtilities.isEventDispatchThread(); // consider app startup a user activity
  }

  @NotNull
  private Queue<Transaction> getQueue(@Nullable TransactionIdImpl transaction) {
    while (transaction != null && transaction.myFinished) {
      transaction = transaction.myParent;
    }
    return transaction == null ? myQueue : transaction.myQueue;
  }

  private void pollQueueLater() {
    invokeLater(() -> {
      Queue<Transaction> queue = getQueue(myCurrentTransaction);
      Transaction next = queue.peek();
      if (next != null && canRunTransactionNow(next, false)) {
        queue.remove();
        runSyncTransaction(next);
      }
    });
  }

  private void runSyncTransaction(@NotNull Transaction transaction) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (Disposer.isDisposed(transaction.parentDisposable)) return;

    boolean wasWritingAllowed = myWritingAllowed;
    myWritingAllowed = true;
    myCurrentTransaction = new TransactionIdImpl(myCurrentTransaction);

    try {
      transaction.runnable.run();
    }
    finally {
      Queue<Transaction> queue = getQueue(myCurrentTransaction.myParent);
      queue.addAll(myCurrentTransaction.myQueue);
      if (!queue.isEmpty()) {
        pollQueueLater();
      }

      myWritingAllowed = wasWritingAllowed;
      myCurrentTransaction.myFinished = true;
      myCurrentTransaction = myCurrentTransaction.myParent;
    }
  }

  @Override
  public void submitTransaction(@NotNull Disposable parentDisposable, @Nullable TransactionId expectedContext, @NotNull Runnable _transaction) {
    final TransactionIdImpl expectedId = (TransactionIdImpl)expectedContext;
    final Transaction transaction = new Transaction(_transaction, expectedId, parentDisposable);
    final Application app = ApplicationManager.getApplication();
    final boolean isDispatchThread = app.isDispatchThread();
    Runnable runnable = () -> {
      if (canRunTransactionNow(transaction, isDispatchThread)) {
        runSyncTransaction(transaction);
      }
      else {
        getQueue(expectedId).offer(transaction);
        pollQueueLater();
      }
    };

    if (isDispatchThread) {
      runnable.run();
    } else {
      invokeLater(runnable);
    }
  }

  private boolean canRunTransactionNow(Transaction transaction, boolean sync) {
    if (sync && !myWritingAllowed) {
      return false;
    }

    TransactionIdImpl currentId = myCurrentTransaction;
    if (currentId == null) {
      return true;
    }

    return transaction.expectedContext != null && currentId.myStartCounter <= transaction.expectedContext.myStartCounter;
  }

  @Override
  public void submitTransactionAndWait(@NotNull final Runnable runnable) throws ProcessCanceledException {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      Transaction transaction = new Transaction(runnable, getContextTransaction(), app);
      if (!canRunTransactionNow(transaction, true)) {
        String message = "Cannot run synchronous submitTransactionAndWait from invokeLater. " +
                         "Please use asynchronous submit*Transaction. " +
                         "See TransactionGuard FAQ for details.\nTransaction: " + runnable;
        if (!isWriteSafeModality(ModalityState.current())) {
          message += "\nUnsafe modality: " + ModalityState.current();
        }
        LOG.error(message);
      }
      runSyncTransaction(transaction);
      return;
    }

    if (app.isReadAccessAllowed()) {
      throw new IllegalStateException("submitTransactionAndWait should not be invoked from a read action");
    }
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Throwable[] exception = {null};
    submitTransaction(Disposer.newDisposable("never disposed"), getContextTransaction(), () -> {
      try {
        runnable.run();
      }
      catch (Throwable e) {
        exception[0] = e;
      }
      finally {
        semaphore.up();
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
    myErrorReported = false;
    boolean allowWriting = userActivity && isWriteSafeModality(ModalityState.current());
    if (myWritingAllowed == allowWriting) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    ApplicationManager.getApplication().assertIsDispatchThread();
    final boolean prev = myWritingAllowed;
    myWritingAllowed = allowWriting;
    return new AccessToken() {
      @Override
      public void finish() {
        myWritingAllowed = prev;
      }
    };
  }

  public boolean isWriteSafeModality(ModalityState state) {
    return Boolean.TRUE.equals(myWriteSafeModalities.get(state));
  }

  public void assertWriteActionAllowed() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (areAssertionsEnabled() && !myWritingAllowed && !myErrorReported) {
      // please assign exceptions here to Peter
      LOG.error(reportWriteUnsafeContext(ModalityState.current()));
      myErrorReported = true;
    }
  }

  private String reportWriteUnsafeContext(@NotNull ModalityState modality) {
    return "Write-unsafe context! Model changes are allowed from write-safe contexts only. " +
           "Please ensure you're using invokeLater/invokeAndWait with a correct modality state (not \"any\"). " +
           "See TransactionGuard documentation for details." +
           "\n  current modality=" + modality +
           "\n  known modalities:\n" +
           StringUtil.join(myWriteSafeModalities.entrySet(),
                           entry -> String.format("    %s, writingAllowed=%s", entry.getKey(), entry.getValue()), ";\n");
  }

  @Override
  public void assertWriteSafeContext(@NotNull ModalityState modality) {
    if (!isWriteSafeModality(modality) && areAssertionsEnabled()) {
      // please assign exceptions here to Peter
      LOG.error(reportWriteUnsafeContext(modality));
    }
  }

  private static boolean areAssertionsEnabled() {
    Application app = ApplicationManager.getApplication();
    if (app instanceof ApplicationEx && !((ApplicationEx)app).isLoaded()) {
      return false;
    }
    return Registry.is("ide.require.transaction.for.model.changes", false);
  }

  @Override
  public void submitTransactionLater(@NotNull final Disposable parentDisposable, @NotNull final Runnable transaction) {
    final TransactionIdImpl id = getContextTransaction();
    final ModalityState startModality = ModalityState.defaultModalityState();
    invokeLater(() -> {
      boolean allowWriting = ModalityState.current() == startModality;
      AccessToken token = startActivity(allowWriting);
      try {
        submitTransaction(parentDisposable, id, transaction);
      }
      finally {
        token.finish();
      }
    });
  }

  private static void invokeLater(Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any(), Condition.FALSE);
  }

  @Override
  public TransactionIdImpl getContextTransaction() {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      return myModality2Transaction.get(ModalityState.defaultModalityState());
    }

    return myWritingAllowed ? myCurrentTransaction : null;
  }

  public void enteredModality(@NotNull ModalityState modality) {
    TransactionIdImpl contextTransaction = getContextTransaction();
    if (contextTransaction != null) {
      myModality2Transaction.put(modality, contextTransaction);
    }
    myWriteSafeModalities.put(modality, myWritingAllowed);
  }

  @Nullable
  public TransactionIdImpl getModalityTransaction(@NotNull ModalityState modalityState) {
    return myModality2Transaction.get(modalityState);
  }

  @NotNull
  public Runnable wrapLaterInvocation(@NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
    if (isWriteSafeModality(modalityState)) {
      return new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().assertIsDispatchThread();
          final boolean prev = myWritingAllowed;
          myWritingAllowed = true;
          try {
            runnable.run();
          } finally {
            myWritingAllowed = prev;
          }
        }

        @Override
        public String toString() {
          return runnable.toString();
        }
      };
    }

    return runnable;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("currentTransaction", myCurrentTransaction)
      .add("writingAllowed", myWritingAllowed)
      .toString();
  }

  private static class Transaction {
    @NotNull  final Runnable runnable;
    @Nullable final TransactionIdImpl expectedContext;
    @NotNull  final Disposable parentDisposable;

    Transaction(@NotNull Runnable runnable, @Nullable TransactionIdImpl expectedContext, @NotNull Disposable parentDisposable) {
      this.runnable = runnable;
      this.expectedContext = expectedContext;
      this.parentDisposable = parentDisposable;
    }
  }

  private static class TransactionIdImpl implements TransactionId {
    private static final AtomicLong ourTransactionCounter = new AtomicLong();
    final long myStartCounter = ourTransactionCounter.getAndIncrement();
    final Queue<Transaction> myQueue = new LinkedBlockingQueue<>();
    boolean myFinished;
    final TransactionIdImpl myParent;

    public TransactionIdImpl(@Nullable TransactionIdImpl parent) {
      myParent = parent;
    }

    @Override
    public String toString() {
      return "Transaction " + myStartCounter + (myFinished ? "(finished)" : "");
    }
  }
}