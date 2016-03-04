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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

/**
 * A service managing model transactions.<p/>
 *
 * A transaction ensures that IntelliJ model (PSI, documents, VFS, project roots etc.) isn't modified in an unexpected way
 * while working with it, with either read or write access. The main property of transactions is isolation: at most one transaction
 * can be running at any given time. The code inside transaction can perform read or write actions and, more importantly, show dialogs
 * and process UI events in other ways: it's guaranteed that no one will be able to sneak in with an unexpected model change using
 * {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or analogs.<p/>
 *
 * Transactions are run on UI thread. They have read access by default. All write actions should be performed inside a transaction.<p/>
 *
 * The recommended way to perform a transaction is to invoke {@link #submitTransaction(Runnable)}. It either runs the transaction immediately
 * (if on UI thread and there's no other transaction running) or queues it to invoke at some later moment, when it becomes possible.<p/>
 *
 * Sometimes transactions need to be processed immediately, even if another transaction is already running. Example:
 * outer transaction has shown a dialog with an editor, and typing into that editor (which requires a transaction for changing document)
 * should be allowed. For such cases, the framework should be notified which transaction kinds are allowed to merged into
 * the main transaction and executed immediately. Use {@link #acceptNestedTransactions(TransactionKind...)} for that. Inner transactions
 * should be given some kind in such circumstances: {@link #submitMergeableTransaction(TransactionKind, Runnable)}.
 *
 * <p><h1>FAQ</h1></p>
 *
 * Q: I've got <b>"Write access is allowed from model transactions only"</b> exception, what do I do?<br/>
 * A: Add a transaction somewhere into the call stack, to the outermost callee where having read/write model consistency is needed.
 * If it's a user action, transaction should be synchronous (see {@link #startSynchronousTransaction(TransactionKind)}. For AnAction
 * inheritors, {@link WrapInTransaction} annotation might be handy. Note that not all actions need to be wrapped into transactions, only
 * those that require the model to be consistent. For example, actions that display settings dialogs or VCS actions are most likely exempt.
 * <p/>
 *
 * If the exception occurs not inside a user action, it's probably from some kind of "invokeLater".
 * Then, replace "invokeLater" with {@link #submitTransaction(Runnable)} or
 * {@link #submitMergeableTransaction(TransactionKind, Runnable)} call.<p/>
 *
 * Q: I've got <b>"Nested transactions are not allowed"</b> exception, what do I do?<br/>
 * A: First, find the place in the stack where the outer transaction is started. Then, see if there is any Swing event pumping
 * in between two transactions (e.g. a dialog is shown). If not, one of two transactions is superfluous, remove it. If there
 * is event pumping, check if the client code (e.g. the one showing the dialog) is prepared to the nested model modifications
 * of the specified kinds. For example, refactoring dialogs might be prepared to {@link TransactionKind#TEXT_EDITING} kind
 * (for text field editing inside the dialogs) but not
 * other model changes, e.g. root changes. The outer transaction code might then specify which kinds it's prepared to (by using
 * {@link #acceptNestedTransactions(TransactionKind...)}), and the inner transaction code should have the very same transaction kind
 * (by using {@link #submitMergeableTransaction(TransactionKind, Runnable)} or {@link #startSynchronousTransaction(TransactionKind)}).
 * If the nested transaction is not expected by the outer code, it must be made asynchronous by using either {@link #submitTransaction(Runnable)}
 * or {@link #submitMergeableTransaction(TransactionKind, Runnable)}.
 * <p/>
 *
 * Q: What's the difference between transactions and read/write actions and commands ({@link com.intellij.openapi.command.CommandProcessor})?<br/>
 * A: Transactions are more abstract and can contain several write actions and even commands inside. Read/write actions guarantee that no
 * one else will modify the model, while transactions allow for some modification, but in a way controlled by transaction kinds. Commands
 * are used for tracking document changes for undo/redo functionality, so they're orthogonal to transactions.
 *
 * @see Application#runReadAction(Runnable)
 * @see Application#runWriteAction(Runnable)
 * @since 146.*
 * @author peter
 */
public abstract class TransactionGuard {

  public static TransactionGuard getInstance() {
    return ServiceManager.getService(TransactionGuard.class);
  }

  /**
   * Ensures that some code will be run in a transaction. It's guaranteed that no other transactions are run at the same time.
   * The code will be run on Swing thread immediately or after all other queued transactions (if any) have been completed.<p/>
   *
   * For more advanced version, see {@link #submitMergeableTransaction(TransactionKind, Runnable)}.
   * Transactions submitted via this method use {@link TransactionKind#NO_MERGE} kind.
   *
   * @param transaction code to execute inside a transaction.
   */
  public static void submitTransaction(@NotNull Runnable transaction) {
    getInstance().submitMergeableTransaction(TransactionKind.NO_MERGE, transaction);
  }

  /**
   * Schedules a transaction and waits for it to be completed. Only allowed to be invoked on non-UI thread and outside read action.
   * @see #submitMergeableTransaction(TransactionKind, Runnable)
   * @param kind
   * @param transaction
   * @throws ProcessCanceledException if current thread is interrupted
   */
  public abstract void submitTransactionAndWait(@NotNull TransactionKind kind, @NotNull Runnable transaction) throws ProcessCanceledException;

  /**
   * A synchronous version of {@link #submitMergeableTransaction(TransactionKind, Runnable)}.
   * @return a token object for this transaction. Call {@link AccessToken#finish()} (inside finally) when the transaction is complete.
   */
  @NotNull
  public abstract AccessToken startSynchronousTransaction(@NotNull TransactionKind kind);

  /**
   * @return whether there's a transaction currently running
   */
  public abstract boolean isInsideTransaction();

  /**
   * When on UI thread and there's no other transaction running, executes the given runnable. If there is a transaction running,
   * but the given {@code kind} is allowed via {@link #acceptNestedTransactions(TransactionKind...)}, merges two transactions
   * and executes the provided code immediately. Otherwise
   * adds the runnable to a queue. When all transactions scheduled before this one are finished, executes the given
   * runnable under a transaction.
   * @param kind a kind object to enable transaction merging or {@link TransactionKind#NO_MERGE}, if no merging is required.
   * @param transaction code to execute inside a transaction.
   */
  public abstract void submitMergeableTransaction(@NotNull TransactionKind kind, @NotNull Runnable transaction);

  /**
   * Allow incoming transactions of the specified kinds to be executed immediately, instead of being queued until the current transaction is finished.<p/>
   *
   * Example: outer transaction has shown a dialog with an editor, and typing into that editor (which requires a transaction for changing document)
   * should be allowed.
   * @param kinds kinds of transactions to allow
   * @return a token object for this session. Please call {@link AccessToken#finish()} (inside finally clause) when you don't want
   * nested transactions anymore.
   */
  @NotNull
  public abstract AccessToken acceptNestedTransactions(TransactionKind... kinds);
}
