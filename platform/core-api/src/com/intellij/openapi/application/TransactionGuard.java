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
 * Transactions are run on UI thread. They have read access by default. All write actions that don't happen as the result of direct user input
 * should be performed inside a transaction. No write action should be performed from within an {@code invokeLater}-like call
 * unless wrapped into a transaction.<p/>
 *
 * The recommended way to perform a transaction is to invoke {@link #submitTransaction(Disposable, Runnable)}. It either runs the transaction immediately
 * (if on UI thread and not inside invokeLater) or queues it to invoke at some later moment, when it becomes possible.<p/>
 *
 * Sometimes transactions need to be processed immediately, even if another transaction is already running. Example:
 * outer transaction has shown a dialog with an editor, and typing into that editor (which requires a transaction for changing document)
 * should be allowed. For such cases, the framework should be notified which transaction kinds are allowed to merged into
 * the main transaction and executed immediately. Use {@link #acceptNestedTransactions(TransactionKind...)} for that. Inner transactions
 * should be given some kind in such circumstances: {@link #submitMergeableTransaction(Disposable, TransactionKind, Runnable)}.
 *
 * <p><h1>FAQ</h1></p>
 *
 * Q: How large/long should transactions be?
 * A: As short as possible, but not shorter. Take them for minimal period of time that you need the model you're working with
 * to be consistent. If your action doesn't display any modal progresses or dialogs, transaction can be omitted.
 * If the action only displays a dialog (e.g. Settings) and does nothing else, and that dialog is ready to possible PSI/VFS events,
 * there should also be no transaction for all the dialog showing time. Actions inside the dialog should care of transactions themselves.
 * If the dialog isn't prepared to any model changes from outside, a transaction around showing the dialog is advised.<p/>
 *
 * The most complicated case is when the action both displays modal dialogs and performs modifications. The only case when those dialogs
 * should be shown under a transaction is when the mere reason of their showing lies somewhere in PSI/VFS/project model, and they are not
 * prepared to foreign code affecting the state of things at the moment of showing. For example, dialogs asking for making files writable
 * are shown only because VFS indicates the file is read-only. So they wouldn't make sense if they allowed modifications to that file
 * while they're shown. Their clients are not prepared to such changes either. So such dialogs should be shown under the same transaction,
 * as the following meaningful modifications performed by the action. Most refactoring dialogs are similar and refactoring actions should
 * take transactions for the whole refactoring process, with all the dialogs inside.<p/>
 *
 * But note that some background processes may need occasional transactions, and will therefore be paused until the dialog is closed.
 * Therefore, it's still advisable that the transactions be as short as possible and preferably exclude any modal dialogs
 * for which transaction-ness is not critical. So a better overall strategy would be to either make the dialogs non-modal,
 * or at least make them and the code that shows them prepared for possible model changes while the dialog is shown.<p/>
 *
 * Dialogs that have per-project modality must never be shown from a transaction, because this would disallow making changes in another
 * project: they'd be blocked by the running transaction.
 *
 * Q: I've got <b>"Write access is allowed from model transactions only"</b> exception, what do I do?<br/>
 * A: You're likely inside an "invokeLater"-like call. Please consider replacing it with {@link #submitTransaction(Disposable, Runnable)}  or
 * {@link #submitMergeableTransaction(Disposable, TransactionId, Runnable)}
 * <p/>
 *
 * Q: What's the difference between transactions and read/write actions and commands ({@link com.intellij.openapi.command.CommandProcessor})?<br/>
 * A: Transactions are more abstract and can contain several write actions and even commands inside. Read/write actions guarantee that no
 * one else will modify the model, while transactions allow for some modification, but in a way controlled by transaction kinds. Commands
 * are used for tracking document changes for undo/redo functionality, so they're orthogonal to transactions.
 *
 * @see Application#runReadAction(Runnable)
 * @see Application#runWriteAction(Runnable)
 * @since 2016.2
 * @author peter
 */
public abstract class TransactionGuard {

  public static TransactionGuard getInstance() {
    return ServiceManager.getService(TransactionGuard.class);
  }

  /**
   * Same as {@link #submitTransaction(Disposable, Runnable)}, but without any parent disposable.
   */
  public static void submitTransaction(@NotNull Runnable transaction) {
    submitTransaction(ApplicationManager.getApplication(), transaction);
  }

  /**
   * Ensures that some code will be run in a transaction. It's guaranteed that no other transactions can run at the same time,
   * except for the ones started from within this runnable. The code will be run on Swing thread immediately
   * or after other queued transactions (if any) have been completed.<p/>
   *
   * For more advanced version, see {@link #submitMergeableTransaction(Disposable, TransactionId, Runnable)}.
   *
   * @param parentDisposable an object whose disposing (via {@link com.intellij.openapi.util.Disposer} makes this transaction invalid,
   *                         and so it won't be run after it has been disposed
   * @param transaction code to execute inside a transaction.
   */
  public static void submitTransaction(@NotNull Disposable parentDisposable, @NotNull Runnable transaction) {
    getInstance().submitMergeableTransaction(parentDisposable, TransactionKind.ANY_CHANGE, transaction);
  }

  /**
   * Runs the given code synchronously inside a transaction. Fails if transactions of given kind are not allowed at this moment.
   * @see #startSynchronousTransaction(TransactionKind)
   */
  public static void syncTransaction(@NotNull TransactionKind kind, @NotNull Runnable transaction) {
    AccessToken token = getInstance().startSynchronousTransaction(kind);
    try {
      transaction.run();
    } finally {
      token.finish();
    }
  }

  /**
   * Schedules a given runnable to be executed inside a transaction later on Swing thread.
   * Same as {@link #submitTransaction(Disposable, Runnable)}, but the runnable is never executed immediately.
   */
  public abstract void submitTransactionLater(@NotNull Disposable parentDisposable, @NotNull Runnable transaction);

  /**
   * Schedules a transaction and waits for it to be completed. Fails if invoked on UI thread inside an incompatible transaction,
   * or inside a read action on non-UI thread.
   * @see #submitMergeableTransaction(Disposable, TransactionKind, Runnable)
   * @throws ProcessCanceledException if current thread is interrupted
   */
  public abstract void submitTransactionAndWait(@NotNull TransactionKind kind, @NotNull Runnable transaction) throws ProcessCanceledException;

  /**
   * A synchronous version of {@link #submitMergeableTransaction(Disposable, TransactionKind, Runnable)}.
   * @return a token object for this transaction. Call {@link AccessToken#finish()} (inside finally) when the transaction is complete.
   */
  @NotNull
  public abstract AccessToken startSynchronousTransaction(@NotNull TransactionKind kind);

  /**
   * Same as {@link #submitMergeableTransaction(Disposable, TransactionKind, Runnable)} with no parent disposable.
   */
  public void submitMergeableTransaction(@NotNull TransactionKind kind, @NotNull Runnable transaction) {
    submitMergeableTransaction(ApplicationManager.getApplication(), kind, transaction);
  }

  /**
   * When on UI thread and there's no other transaction running, executes the given runnable. If there is a transaction running,
   * but the given {@code kind} is allowed via {@link #acceptNestedTransactions(TransactionKind...)}, merges two transactions
   * and executes the provided code immediately. Otherwise
   * adds the runnable to a queue. When all transactions scheduled before this one are finished, executes the given
   * runnable under a transaction.
   * @param parentDisposable an object whose disposing (via {@link com.intellij.openapi.util.Disposer} makes this transaction invalid,
   *                         and so it won't be run after it has been disposed.
   * @param kind a "kind" object for transaction merging
   * @param transaction code to execute inside a transaction.
   */
  public abstract void submitMergeableTransaction(@NotNull Disposable parentDisposable, @NotNull TransactionKind kind, @NotNull Runnable transaction);

  /**
   * Executes the given runnable inside a transaction as soon as possible on the UI thread. The runnable is executed either when there's
   * no active transaction running, or when the running transaction has the same (or compatible) id as {@code mergeInto}. If the id of
   * the current transaction is passed, the transaction is executed immediately. Otherwise adds the runnable to a queue,
   * to execute after all transactions scheduled before this one are finished.
   * @param parentDisposable an object whose disposing (via {@link com.intellij.openapi.util.Disposer} makes this transaction invalid,
   *                         and so it won't be run after it has been disposed.
   * @param mergeInto an optional id of another transaction, to allow execution inside that transaction if it's still running
   * @param transaction code to execute inside a transaction.
   * @see #getCurrentMergeableTransaction()
   */
  public abstract void submitMergeableTransaction(@NotNull Disposable parentDisposable, @Nullable TransactionId mergeInto, @NotNull Runnable transaction);

  /**
   * Allow incoming transactions of the specified kinds to be executed immediately, instead of being queued until the current transaction is finished.<p/>
   *
   * Example: outer transaction has shown a dialog with an editor, and typing into that editor (which requires a transaction for changing document).
   * should be allowed.<p/>
   *
   * For dialogs, consider using {@link AcceptNestedTransactions} annotation instead of explicit call to this method.
   * @param kinds kinds of transactions to allow
   * @return a token object for this session. Please call {@link AccessToken#finish()} (inside finally clause) when you don't want
   * nested transactions anymore.
   */
  @NotNull
  public abstract AccessToken acceptNestedTransactions(@NotNull TransactionKind... kinds);

  /**
   * Asserts that a transaction is currently running, or not. Callable only on Swing thread.
   * @param transactionRequired whether the assertion should check that the application is inside transaction or not
   * @param errorMessage the message that will be logged if current transaction status differs from the expected one
   */
  public abstract void assertInsideTransaction(boolean transactionRequired, @NotNull String errorMessage);

  /**
   * @return the id of the currently running transaction for using in {@link #submitMergeableTransaction(Disposable, TransactionId, Runnable)},
   * or null if there's no transaction running or merging is not allowed in the callee context (e.g. from invokeLater).
   */
  public abstract TransactionId getCurrentMergeableTransaction();
}
