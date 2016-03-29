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
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.impl.DebugUtil;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.TransactionGuardImpl");
  private final Queue<Runnable> myQueue = new LinkedBlockingQueue<Runnable>();
  private final Set<TransactionKind> myMergeableKinds = ContainerUtil.newHashSet();
  private String myTransactionStartTrace;
  private ModalityState myTransactionModality;
  private boolean myUserActivity;

  @Override
  @NotNull
  public AccessToken startSynchronousTransaction(@NotNull TransactionKind kind) throws IllegalStateException {
    ModalityState modality = ModalityState.current();
    if (isInsideTransaction()) {
      if (modality.equals(myTransactionModality) || myUserActivity) {
        return AccessToken.EMPTY_ACCESS_TOKEN;
      }

      if (myMergeableKinds.contains(kind)) {
        final ModalityState prev = myTransactionModality;
        myTransactionModality = modality;
        return new AccessToken() {
          @Override
          public void finish() {
            myTransactionModality = prev;
          }
        };
      }

      // please assign exceptions that occur here to Peter
      LOG.error("Nested transactions are not allowed, see FAQ in TransactionGuard class javadoc. Transaction start trace is in attachment. Kind is " + kind,
                new Attachment("trace.txt", myTransactionStartTrace));
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }
    myTransactionModality = modality;
    myTransactionStartTrace = DebugUtil.currentStackTrace();
    return new AccessToken() {
      @Override
      public void finish() {
        myTransactionStartTrace = null;
        myTransactionModality = null;
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
        if (isInsideTransaction()) return;

        Runnable next = myQueue.poll();
        if (next != null) {
          runSyncTransaction(TransactionKind.ANY_CHANGE, next);
        }
      }
    }, app.getDisposed());
  }

  private void runSyncTransaction(@NotNull TransactionKind kind, @NotNull Runnable code) {
    AccessToken token = startSynchronousTransaction(kind);
    try {
      code.run();
    }
    finally {
      token.finish();
    }
  }

  private boolean isInsideTransaction() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myTransactionStartTrace != null;
  }

  @Override
  public void submitMergeableTransaction(@Nullable final Disposable parentDisposable, @NotNull final TransactionKind kind, @NotNull final Runnable _transaction) {
    @NotNull final Runnable transaction = parentDisposable == null ? _transaction : new Runnable() {
      @Override
      public void run() {
        if (!Disposer.isDisposed(parentDisposable)) {
          _transaction.run();
        }
      }
    };
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (canRunTransactionNow(kind)) {
          runSyncTransaction(kind, transaction);
        }
        else {
          myQueue.offer(transaction);
          pollQueueLater();
        }
      }
    };

    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    } else {
      //todo add ModalityState.any() when write actions are required to run under a guard
      app.invokeLater(runnable, app.getDisposed());
    }
  }

  protected boolean canRunTransactionNow(@NotNull TransactionKind kind) {
    return !isInsideTransaction() || myMergeableKinds.contains(kind) || ModalityState.current().equals(myTransactionModality);
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
  public void submitTransactionAndWait(@NotNull TransactionKind kind, @NotNull final Runnable transaction) throws ProcessCanceledException {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      if (!canRunTransactionNow(kind)) {
        throw new AssertionError("Cannot run submitTransactionAndWait from another transaction, kind " + kind + " is not allowed");
      }
      runSyncTransaction(kind, transaction);
      return;
    }

    assert !app.isReadAccessAllowed() : "submitTransactionAndWait should not be invoked from a read action";
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Throwable[] exception = {null};
    submitMergeableTransaction(null, kind, new Runnable() {
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
  public <T extends Throwable> void performUserActivity(Runnable activity) {
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
    if (myUserActivity == userActivity) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    final boolean prev = myUserActivity;
    myUserActivity = userActivity;
    return new AccessToken() {
      @Override
      public void finish() {
        myUserActivity = prev;
      }
    };
  }

  public boolean isWriteActionAllowed() {
    return !Registry.is("ide.require.transaction.for.model.changes", false) || isInsideTransaction() || myUserActivity;
  }

  @Override
  public void submitTransactionLater(@Nullable final Disposable parentDisposable, @NotNull final Runnable transaction) {
    Application app = ApplicationManager.getApplication();
    app.invokeLater(new Runnable() {
      @Override
      public void run() {
        submitMergeableTransaction(parentDisposable, TransactionKind.ANY_CHANGE, transaction);
      }
    }, app.getDisposed());
  }
}
