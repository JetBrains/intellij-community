// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A service managing write-safe contexts, ensuring that no one will be able to perform an unexpected model change using
 * {@link javax.swing.SwingUtilities#invokeLater} or analogs while a dialog is shown.
 * See more details in {@link ModalityState} documentation.<p/>
 *
 * Write actions that modify model are only allowed inside write-safe contexts. These are:
 * <ul>
 *   <li>Direct user activity processing (key/mouse presses, actions) in non-modal state</li>
 *   <li>User activity processing in a modality state that was started (e.g. by showing a dialog or progress) in a write-safe context</li>
 *   <li>{@link Application#invokeLater(Runnable, ModalityState)} calls with a modality state that's either non-modal
 *   or was started inside a write-safe context</li>
 * </ul>
 *
 * All other contexts are considered write-unsafe, and model modifications are not allowed from them.
 * This includes Swing {@code invokeLater}, action update, renderers etc.
 * Even if user activity happens in such context
 * (e.g. someone clicks a button in a dialog shown from {@link javax.swing.SwingUtilities#invokeLater(Runnable)}),
 * this will result in an exception.
 *
 * If you've got <b>"Model changes are allowed from write-safe contexts only"</b>
 *    or <b>"Cannot run synchronous submitTransactionAndWait"</b> exception, then
 * you're likely inside a Swing's "invokeLater" or "invokeAndWait"-like call.
 * <ul>
 * <li/> Consider simplifying the code. For example, if the outer code is known to be run on EDT, "invokeLater(AndWait)IfNeeded" call can be removed. If it's in a pooled thread, the "IfNeeded" part can be removed. "invokeAndWaitIfNeeded" can be safely unwrapped, if already on EDT.
 * <li/> Consider making the code synchronous. "invokeLater" from EDT rarely makes sense and can often be replaced with synchronous execution (which will likely be inside a user activity: a write-safe context).
 * <li/> If you get the assertion from synchronous document commit or VFS refresh, consider making them asynchronous. For documents, use {@link com.intellij.psi.PsiDocumentManager#performLaterWhenAllCommitted(Runnable)}.
 * You can also try to get rid of them at all, by making your code work only with VFS and PSI and assuming
 * they're refreshed/committed often enough by some other code.
 * <li/> If you still have "invokeAndWaitIfNeeded" call with model-changing activity inside, replace it with {@link Application#invokeAndWait}.
 * You might still get assertions after that, that would mean that some caller down the stack is also using "invokeLater"-like call and needs to be fixed as well.
 * <li/> If you still absolutely need "invokeLater", use {@link Application#invokeLater} or {@link com.intellij.ui.GuiUtils#invokeLaterIfNeeded}),
 * and pass a modality state that appeared in a write-safe context (e.g. a background progress started in an action).
 * Most likely {@link ModalityState#defaultModalityState()} will do.
 * Don't forget to check inside the "later" runnable that it's still actual, that the model haven't been changed by someone else since its scheduling.
 * </ul>
 * <p/>
 *
 * @see ModalityState
 * @author peter
 */
public abstract class TransactionGuard {
  private static volatile TransactionGuard ourInstance = CachedSingletonsRegistry.markCachedField(TransactionGuard.class);

  public static TransactionGuard getInstance() {
    TransactionGuard instance = ourInstance;
    if (instance == null) {
      instance = ApplicationManager.getApplication().getService(TransactionGuard.class);
      ourInstance = instance;
    }
    return instance;
  }

  /**
   * @deprecated in a definitely write-safe context, just replace this call with {@code transaction} contents.
   * Otherwise, replace with {@link Application#invokeLater} and take care that the default or explicitly passed modality state is write-safe.
   * When in doubt, use {@link ModalityState#NON_MODAL}.
   */
  @Deprecated
  public static void submitTransaction(@NotNull Disposable parentDisposable, @NotNull Runnable transaction) {
    TransactionGuard guard = getInstance();
    guard.submitTransaction(parentDisposable, guard.getContextTransaction(), transaction);
  }

  /**
   * Logs an error if the given modality state was created in a write-unsafe context. For modalities created in write-safe contexts,
   * {@link Application#invokeLater(Runnable, ModalityState)} and similar calls will be guaranteed to also run in a write-safe context.
   * {@link ModalityState#NON_MODAL} is always write-safe, {@link ModalityState#any()} is always write-unsafe.
   */
  public abstract void assertWriteSafeContext(@NotNull ModalityState modality);

  /**
   * Checks whether the current state allows for writing the model. Must be called from write thread.
   * @return {@code true} is current context is write-safe, {@code false} otherwise
   */
  public abstract boolean isWritingAllowed();

  /**
   * Checks whether a given modality is write-safe.
   *
   * @param state modality to check
   * @return {@code true} if a given modality is write-safe, {@code false} otherwise
   */
  public abstract boolean isWriteSafeModality(@NotNull ModalityState state);

  /**
   * @deprecated Replace with {@link Application#invokeLater} and take care that the default or explicitly passed modality state is write-safe.
   * When in doubt, use {@link ModalityState#NON_MODAL}.
   */
  @Deprecated
  public abstract void submitTransactionLater(@NotNull Disposable parentDisposable, @NotNull Runnable transaction);

  /**
   * @deprecated if called on Swing thread, just replace this call with {@code transaction} contents.
   * Otherwise, replace with {@link Application#invokeAndWait} and take care that the default or explicitly passed modality state is write-safe.
   * When in doubt, use {@link ModalityState#NON_MODAL}.
   */
  @Deprecated
  public abstract void submitTransactionAndWait(@NotNull Runnable transaction) throws ProcessCanceledException;

  /**
   * @deprecated see {@link #submitTransaction(Disposable, Runnable)}.
   */
  @Deprecated
  public abstract void submitTransaction(@NotNull Disposable parentDisposable, @Nullable TransactionId expectedContext, @NotNull Runnable transaction);

  /**
   * @deprecated replace with {@link ModalityState#defaultModalityState()} and use the result for "invokeLater" when replacing "submitTransaction" calls.
   */
  @Nullable
  @Deprecated
  public abstract TransactionId getContextTransaction();
}
