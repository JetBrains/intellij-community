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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A service managing model transactions.<p/>
 *
 * A transaction ensures that IntelliJ model (PSI, documents, VFS, project roots etc.) isn't modified in an unexpected way
 * while working with it, with either read or write access. The main property of transactions is mutual exclusion: at most one transaction
 * can be running at any given time. The code inside transaction can perform read or write actions and, more importantly, show dialogs
 * and process UI events in other ways: it's guaranteed that no one will be able to sneak in with an unexpected model change using
 * {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or analogs.<p/>
 *
 * Transactions are run on UI thread and have read access. Write actions that modify model are only allowed inside write-safe contexts. These are:
 * <ul>
 *   <li>Transactions</li>
 *   <li>Direct user activity processing (key/mouse presses, actions)</li>
 *   <li>{@link Application#invokeLater(Runnable, ModalityState)} calls with a modality state that's either non-modal
 *   or was started inside a write-safe context (for example, a dialog shown from an action wrapped into a transaction)</li>
 * </ul>
 *
 * All other contexts are considered write-unsafe, and model modifications are not allowed from them.
 * Even if user activity happens in such context
 * (e.g. someone clicks a button in a dialog shown from {@link javax.swing.SwingUtilities#invokeLater(Runnable)}),
 * this will result in an assertion.
 * Synchronous transactions ({@link #submitTransactionAndWait(Runnable)} are also not allowed from such invokeLater calls.
 *
 * The recommended way to perform a transaction is to invoke {@link #submitTransaction(Disposable, Runnable)}. It either runs the transaction immediately
 * (if in write-safe context on UI thread) or queues it to invoke at some later moment, when it becomes possible.<p/>
 *
 * Sometimes transactions need to be processed as soon as possible, even if another transaction is already running. Example:
 * outer transaction has shown a dialog with a modal progress that performs a write action inside (which requires a transaction)
 * and waits for it to be finished. For such cases, a context transaction id
 * should be supplied to the nested transaction via {@link #submitTransaction(Disposable, TransactionId, Runnable)}.
 *
 * <p><h1>FAQ</h1></p>
 *
 * Q: When should transactions be used?
 * A: Whenever the code inside isn't prepared to model being modified from the outside world. Which is, almost always. Well known base AnAction
 * classes that work with PSI are wrapped into transactions by default. It only makes sense to opt out (by overriding AnAction#startInTransaction), if your actions
 * don't modify the PSI/document/VFS model in any way, and can be invoked in a dialog that's shown from invokeLater.
 * <p/>
 *
 * Q: I've got <b>"Write access is allowed from model transactions only"</b>
 *    or <b>"Cannot run synchronous submitTransactionAndWait"</b> exception, what do I do?<br/>
 * A: You're likely inside an "invokeLater" or "invokeAndWait"-like call.
 * <ul>
 * <li/> If this code is showing a dialog that requires model consistency during its lifetime,
 * consider replacing invokeLater with {@link #submitTransaction(Disposable, Runnable)} or
 * {@link #submitTransaction(Disposable, TransactionId, Runnable)}.<br/>
 * <li/> Consider simplifying the code. For example, if the outer code is known to be run on EDT, "invokeLater(AndWait)IfNeeded" call can be removed. If it's in a pooled thread, the "IfNeeded" part can be removed. "invokeAndWaitIfNeeded" can be safely unwrapped, if already on EDT.
 * <li/> Consider making the code synchronous. "invokeLater" from EDT rarely makes sense and can often be replaced with synchronous execution (which will likely be inside a user activity: a write-safe context).
 * <li/> If you get the assertion from synchronous document commit or VFS refresh, consider making them asynchronous. For documents, use {@link com.intellij.psi.PsiDocumentManager#performLaterWhenAllCommitted(Runnable)}.
 * You can also try to get rid of them at all, by making your code work only with VFS and PSI and assuming
 * they're refreshed/committed often enough by some other code.
 * <li/> If you still have "invokeAndWaitIfNeeded" call with model-changing activity inside, replace it with {@link #submitTransactionAndWait(Runnable)} or {@link Application#invokeAndWait}.
 * You might still get assertions after that, that would mean that some caller down the stack is also using "invokeLater"-like call and needs to be fixed as well.
 * <li/> If you still absolutely need "invokeLater" use {@link Application#invokeLater} (or GuiUtils for "invokeLaterIfNeeded"),
 * and pass a modality state that appeared in a write-safe context (e.g. a background progress started in an action).
 * Most likely {@link ModalityState#defaultModalityState()} will do.
 * Don't forget to check inside the "later" runnable that it's still actual, that the model haven't been changed by someone else since its scheduling.
 * </ul>
 * <p/>
 *
 * Q: What's the difference between transactions and read/write actions and commands ({@link com.intellij.openapi.command.CommandProcessor})?<br/>
 * A: Transactions are more abstract and can contain several write actions and even commands inside. Read/write actions guarantee that no
 * one else will modify the model, while transactions allow for some modification, but in a controlled way. Commands
 * are used for tracking document changes for undo/redo functionality, so they're orthogonal to transactions.
 *
 * @see Application#runReadAction(Runnable)
 * @see Application#runWriteAction(Runnable)
 * @since 2016.2
 * @author peter
 */
public abstract class TransactionGuard {
  private static volatile TransactionGuard ourInstance;

  public static TransactionGuard getInstance() {
    TransactionGuard instance = ourInstance;
    if (instance == null) {
      ourInstance = instance = ServiceManager.getService(TransactionGuard.class);
    }
    return instance;
  }

  /**
   * Ensures that some code will be run in a transaction. It's guaranteed that no other transactions can run at the same time,
   * except for the ones started from within this runnable. The code will be run on Swing thread immediately
   * or after other queued transactions (if any) have been completed.<p/>
   *
   * For more advanced version, see {@link #submitTransaction(Disposable, TransactionId, Runnable)}.
   *
   * @param parentDisposable an object whose disposing (via {@link com.intellij.openapi.util.Disposer} makes this transaction invalid,
   *                         and so it won't be run after it has been disposed
   * @param transaction code to execute inside a transaction.
   */
  public static void submitTransaction(@NotNull Disposable parentDisposable, @NotNull Runnable transaction) {
    TransactionGuard guard = getInstance();
    guard.submitTransaction(parentDisposable, guard.getContextTransaction(), transaction);
  }

  /**
   * Schedules a given runnable to be executed inside a transaction later on Swing thread.
   * Same as {@link #submitTransaction(Disposable, Runnable)}, but the runnable is never executed immediately.
   */
  public abstract void submitTransactionLater(@NotNull Disposable parentDisposable, @NotNull Runnable transaction);

  /**
   * Schedules a transaction and waits for it to be completed. Logs an error if invoked on UI thread inside an incompatible transaction,
   * throws {@link IllegalStateException} inside a read action on non-UI thread.
   * @see #submitTransaction(Disposable, TransactionId, Runnable)
   * @throws ProcessCanceledException if current thread is interrupted
   */
  public abstract void submitTransactionAndWait(@NotNull Runnable transaction) throws ProcessCanceledException;

  /**
   * Executes the given runnable inside a transaction as soon as possible on the UI thread. The runnable is executed either when there's
   * no active transaction running, or when the running transaction has the same (or compatible) id as {@code expectedContext}. If the id of
   * the current transaction is passed, the transaction is executed immediately. Otherwise adds the runnable to a queue,
   * to execute after all transactions scheduled before this one are finished.
   * @param parentDisposable an object whose disposing (via {@link com.intellij.openapi.util.Disposer} makes this transaction invalid,
   *                         and so it won't be run after it has been disposed.
   * @param expectedContext an optional id of another transaction, to allow execution inside that transaction if it's still running
   * @param transaction code to execute inside a transaction.
   * @see #getContextTransaction()
   */
  public abstract void submitTransaction(@NotNull Disposable parentDisposable, @Nullable TransactionId expectedContext, @NotNull Runnable transaction);

  /**
   * @return the id of the currently running transaction for using in {@link #submitTransaction(Disposable, TransactionId, Runnable)},
   * or null if there's no transaction running or transaction nesting is not allowed in the callee context (e.g. from invokeLater).
   */
  public abstract TransactionId getContextTransaction();
}
