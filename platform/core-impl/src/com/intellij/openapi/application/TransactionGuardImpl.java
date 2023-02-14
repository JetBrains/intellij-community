// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.Objects;

public final class TransactionGuardImpl extends TransactionGuard {
  private static final Logger LOG = Logger.getInstance(TransactionGuardImpl.class);

  /**
   * Remembers the value of {@link #myWritingAllowed} at the start of each modality. If writing wasn't allowed at that moment
   * (e.g. inside SwingUtilities.invokeLater), it won't be allowed for all dialogs inside such modality, even from user activity.
   */
  private final Map<ModalityState, Boolean> myWriteSafeModalities = CollectionFactory.createConcurrentWeakMap();
  private boolean myWritingAllowed;
  private boolean myErrorReported;

  public TransactionGuardImpl() {
    myWriteSafeModalities.put(ModalityState.NON_MODAL, true);
    myWritingAllowed = SwingUtilities.isEventDispatchThread(); // consider app startup a user activity
  }

  @Override
  public void submitTransaction(@NotNull Disposable parentDisposable,
                                @Nullable TransactionId expectedContext,
                                @NotNull Runnable transaction) {
    ModalityState modality = expectedContext == null ? ModalityState.NON_MODAL : ((TransactionIdImpl)expectedContext).myModality;
    Application app = ApplicationManager.getApplication();
    if (app.isWriteIntentLockAcquired() && myWritingAllowed && !ModalityState.current().dominates(modality)) {
      if (!Disposer.isDisposed(parentDisposable)) {
        transaction.run();
      }
    }
    else {
      AppUIExecutor.onWriteThread(modality).later().expireWith(parentDisposable).execute(transaction);
    }
  }

  @Override
  public void submitTransactionAndWait(@NotNull final Runnable runnable) throws ProcessCanceledException {
    Application app = ApplicationManager.getApplication();
    if (app.isWriteIntentLockAcquired()) {
      if (!myWritingAllowed) {
        @NonNls String message = "Cannot run synchronous submitTransactionAndWait from invokeLater. " +
                                 "Please use asynchronous submit*Transaction. " +
                                 "See TransactionGuard FAQ for details.\nTransaction: " + runnable;
        if (!isWriteSafeModality(ModalityState.current())) {
          message += "\nUnsafe modality: " + ModalityState.current();
        }
        LOG.error(message);
      }
      runnable.run();
      return;
    }

    if (app.isReadAccessAllowed()) {
      throw new IllegalStateException("submitTransactionAndWait should not be invoked from a read action");
    }
    ModalityState state = ModalityState.defaultModalityState();
    if (!isWriteSafeModality(state)) {
      LOG.error("Cannot run synchronous submitTransactionAndWait from a background thread created in a write-unsafe context");
    }
    app.invokeAndWait(runnable, state);
  }

  /**
   * An absolutely guru method!<p/>
   * <p>
   * Executes the given code and marks it as a user activity, to allow write actions to be run without requiring transactions.
   * This is only to be called from UI infrastructure, during InputEvent processing and wrap the point where the control
   * goes to custom input event handlers for the first time.<p/>
   * <p>
   * If you wish to invoke some actionPerformed,
   * please consider using {@code ActionManager.tryToExecute()} instead, or ensure in some other way that the action is enabled
   * and can be invoked in the current modality state.
   */
  @ApiStatus.Internal
  public void performUserActivity(Runnable activity) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    performActivity(true, activity);
  }

  /**
   * An absolute guru method, only intended to be used from Swing event processing. Please consult Peter if you think you need to invoke this.
   */
  @ApiStatus.Internal
  public void performActivity(boolean userActivity, @NotNull Runnable runnable) {
    myErrorReported = false;
    boolean allowWriting = userActivity && isWriteSafeModality(ModalityState.current());
    if (myWritingAllowed == allowWriting) {
      runnable.run();
      return;
    }

    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean prev = myWritingAllowed;
    myWritingAllowed = allowWriting;
    try {
      runnable.run();
    }
    finally {
      myWritingAllowed = prev;
    }
  }

  @Override
  public boolean isWritingAllowed() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    return myWritingAllowed;
  }

  @Override
  public boolean isWriteSafeModality(@NotNull ModalityState state) {
    return Boolean.TRUE.equals(myWriteSafeModalities.get(state));
  }

  public void assertWriteActionAllowed() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    if (!myWritingAllowed && areAssertionsEnabled() && !myErrorReported) {
      // please assign exceptions here to Peter
      LOG.error(reportWriteUnsafeContext(ModalityState.current()));
      myErrorReported = true;
    }
  }

  private static @NonNls String reportWriteUnsafeContext(@NotNull ModalityState modality) {
    return "Write-unsafe context! Model changes are allowed from write-safe contexts only. " +
           "Please ensure you're using invokeLater/invokeAndWait with a correct modality state (not \"any\"). " +
           "See TransactionGuard documentation for details." +
           "\n  current modality=" + modality;
  }

  @Override
  public void assertWriteSafeContext(@NotNull ModalityState modality) {
    if (!isWriteSafeModality(modality) && areAssertionsEnabled()) {
      // please assign exceptions here to Peter
      LOG.error(reportWriteUnsafeContext(modality));
    }
  }

  private static boolean areAssertionsEnabled() {
    return LoadingState.COMPONENTS_LOADED.isOccurred() && Registry.is("ide.require.transaction.for.model.changes", false);
  }

  @Override
  public void submitTransactionLater(@NotNull final Disposable parentDisposable, @NotNull final Runnable transaction) {
    TransactionIdImpl ctx = getContextTransaction();
    ApplicationManager.getApplication().invokeLaterOnWriteThread(transaction, ctx == null ? ModalityState.NON_MODAL : ctx.myModality);
  }

  @Override
  public TransactionIdImpl getContextTransaction() {
    if (ApplicationManager.getApplication().isWriteIntentLockAcquired()) {
      if (!myWritingAllowed) {
        return null;
      }
    }
    else if (ProgressIndicatorProvider.getGlobalProgressIndicator() == null) {
      return null;
    }

    ModalityState state = ModalityState.defaultModalityState();
    return isWriteSafeModality(state) ? new TransactionIdImpl(state) : null;
  }

  public void enteredModality(@NotNull ModalityState modality) {
    myWriteSafeModalities.put(modality, myWritingAllowed);
  }

  @NotNull
  public Runnable wrapLaterInvocation(@NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
    return new Runnable() {
      @Override
      public void run() {
        if (isWriteSafeModality(modalityState)) {
          ApplicationManager.getApplication().assertWriteIntentLockAcquired();
          runWithWritingAllowed(runnable);
        }
        else {
          runnable.run();
        }
      }

      @Override
      public String toString() {
        return runnable.toString();
      }
    };
  }

  private void runWithWritingAllowed(@NotNull Runnable runnable) {
    final boolean prev = myWritingAllowed;
    myWritingAllowed = true;
    try {
      runnable.run();
    }
    finally {
      myWritingAllowed = prev;
    }
  }

  @Override
  public String toString() {
    return "TransactionGuardImpl{myWritingAllowed=" + myWritingAllowed + '}';
  }

  private static final class TransactionIdImpl implements TransactionId {
    final ModalityState myModality;

    private TransactionIdImpl(ModalityState modality) {
      myModality = modality;
    }

    @Override
    public String toString() {
      return myModality.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TransactionIdImpl)) return false;
      TransactionIdImpl id = (TransactionIdImpl)o;
      return Objects.equals(myModality, id.myModality);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myModality);
    }
  }
}