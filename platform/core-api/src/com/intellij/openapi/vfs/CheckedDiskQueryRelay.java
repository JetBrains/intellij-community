// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableConvertor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

/// A [DiskQueryRelay] for a function with a checked exception: a Java caller gets the exception of the function as is, with its type.
///
/// The goal of this interface is to provide compile-time checked exceptions for java code: an implementation ([DiskQueryRelay])
/// rethrows the wrapped function's exception anyway, but being in Kotlin, it can't _declare_ `throws E` for a type parameter E,
/// and it means in java code one can't write
/// ```
/// try {
///   var result = DiskQueryRelay.compute(task);
/// }
/// catch(CheckedException ex){ // javac rejects this, as it sees no way code under .compute() can throw CheckedException
///   ...
/// }
/// ```
/// This interface makes E visible for javac.
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface CheckedDiskQueryRelay<P, R, E extends Exception> {

  /// @throws ProcessCanceledException if the caller is cancelled while it waits for the function
  /// @throws E if the function throws an exception
  R accessDiskWithCheckCanceled(@NotNull P arg) throws E;

  @SuppressWarnings("unchecked")
  static <P, R, E extends Exception> @NotNull CheckedDiskQueryRelay<P, R, E> create(@NotNull ThrowableConvertor<? super P, ? extends R, ? extends E> function) {
    // E exists only for javac: seen as RuntimeException, convert() fits a Function, and the relay throws the exception as is
    ThrowableConvertor<P, R, RuntimeException> unchecked = (ThrowableConvertor<P, R, RuntimeException>)function;
    return (CheckedDiskQueryRelay<P, R, E>)new DiskQueryRelay<>(unchecked::convert);
  }

  /// The one-shot form of [#accessDiskWithCheckCanceled]: see [DiskQueryRelay#compute(ThrowableComputable)]
  static <R, E extends Exception> R compute(@NotNull ThrowableComputable<R, E> task) throws E {
    return DiskQueryRelay.compute(task);
  }

  /// The one-shot form of [#accessDiskWithCheckCanceled]: see [DiskQueryRelay#compute(ThrowableComputable, ExecutorService)]
  static <R, E extends Exception> R compute(@NotNull ThrowableComputable<R, E> task, @NotNull ExecutorService executor) throws E {
    return DiskQueryRelay.compute(task, executor);
  }
}
