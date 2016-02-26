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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author peter
 */
public class TransactionGuardImpl extends TransactionGuard {
  private final Queue<Runnable> myQueue = new LinkedBlockingQueue<Runnable>();
  private final Set<TransactionKind> myMergeableKinds = ContainerUtil.newHashSet();
  private boolean myInsideTransaction;

  @Override
  @NotNull
  public AccessToken startSynchronousTransaction(@Nullable TransactionKind kind) throws IllegalStateException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (kind != null && myMergeableKinds.contains(kind)) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }
    if (myInsideTransaction) {
      throw new IllegalStateException("Nested transactions are not allowed");
    }
    myInsideTransaction = true;
    return new AccessToken() {
      @Override
      public void finish() {
        myInsideTransaction = false;
        if (!myQueue.isEmpty()) {
          pollQueueLater();
        }
      }
    };
  }

  private void pollQueueLater() {
    //todo replace with SwingUtilities when write actions are required to run under a guard
    final Application app = ApplicationManager.getApplication();
    app.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myInsideTransaction) return;

        Runnable next = myQueue.poll();
        if (next != null) {
          runSyncTransaction(null, next);
        }
      }
    }, app.getDisposed());
  }

  private void runSyncTransaction(@Nullable TransactionKind kind, @NotNull Runnable code) {
    AccessToken token = startSynchronousTransaction(kind);
    try {
      code.run();
    }
    finally {
      token.finish();
    }
  }

  @Override
  public boolean isInsideTransaction() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myInsideTransaction;
  }

  @Override
  public void submitMergeableTransaction(@Nullable final TransactionKind kind, @NotNull final Runnable transaction) {
    submitTransaction(kind, transaction, ModalityState.defaultModalityState());
  }

  public void submitTransaction(@Nullable final TransactionKind kind,
                                 @NotNull final Runnable transaction,
                                 ModalityState modalityState) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (!myInsideTransaction || kind != null && myMergeableKinds.contains(kind)) {
          runSyncTransaction(kind, transaction);
        }
        else {
          myQueue.offer(transaction);
          pollQueueLater();
        }
      }
    };

    //todo replace with SwingUtilities when write actions are required to run under a guard
    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    } else {
      app.invokeLater(runnable, modalityState, app.getDisposed());
    }
  }

  @Override
  @NotNull
  public AccessToken acceptNestedTransactions(TransactionKind... kinds) {
    if (!isInsideTransaction()) {
      throw new IllegalStateException("acceptNestedTransactions must be called inside a transaction");
    }
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
  public void submitTransactionAndWait(@Nullable TransactionKind kind, @NotNull final Runnable transaction) throws ProcessCanceledException {
    submitTransactionAndWait(kind, transaction, ModalityState.defaultModalityState());
  }

  public void submitTransactionAndWait(@Nullable TransactionKind kind,
                                        @NotNull final Runnable transaction,
                                        ModalityState modalityState) {
    Application app = ApplicationManager.getApplication();
    assert !app.isDispatchThread() : "submitTransactionAndWait should not be invoked on dispatch thread";
    assert !app.isReadAccessAllowed() : "submitTransactionAndWait should not be invoked from a read action";

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Throwable[] exception = {null};
    submitTransaction(kind, new Runnable() {
      @Override
      public void run() {
        try {
          transaction.run();
        }
        catch (Throwable e) {
          exception[0] = e;
        }
        finally {
          semaphore.up();
        }
      }
    }, modalityState);
    semaphore.waitFor();
    if (exception[0] != null) {
      throw new RuntimeException(exception[0]);
    }
  }
}
