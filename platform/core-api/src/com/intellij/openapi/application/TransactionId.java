package com.intellij.openapi.application;

import com.intellij.openapi.Disposable;

/**
 * A unique object identifying each running transaction. Can be retrieved from {@link TransactionGuard#getContextTransaction()}
 * while inside a transaction, and used to merge other transactions into it via
 * {@link TransactionGuard#submitTransaction(Disposable, TransactionId, Runnable)}.
 *
 * @author peter
 * @since 2016.2
 */
public interface TransactionId {
}
