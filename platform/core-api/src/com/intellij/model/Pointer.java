// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * <h3>Example 1</h3>
 * <p>
 * {@linkplain com.intellij.psi.SmartPsiElementPointer Smart pointers} might be used to restore the element across different read actions.
 * </p>
 * <p>
 * Elements are expected to stay valid within a single {@linkplain Application#runReadAction read action}.
 * It's highly advised to split long read actions into several short ones, but this also means
 * that some {@linkplain Application#runWriteAction write action} might be run in between these short read actions,
 * which could potentially change the model of the element (reference model, PSI model, framework model or whatever model).
 * </p>
 * <pre>{@code
 * val pointer = readAction {
 *   val instance = obtainSomeInstanceWhichIsValidWithinAReadAction()
 *   return@readAction instance.createPointer()
 * }
 * // the pointer might be safely stored in the UI or another model for later usage
 * readAction { // another read action
 *   val restoredInstance = pointer.dereference()
 *   if (restoredInstance == null) {
 *     // instance was invalidated, act accordingly
 *     return
 *   }
 *   // at this point the instance is valid because it should've not exist if it's not
 *   doSomething(restoredInstance)
 * }
 *
 * readAction {
 *   // same pointer may be used in several subsequent read actions
 *   val restoredInstance = pointer.dereference()
 *   ...
 * }
 * }</pre>
 *
 * <h3>Example 2</h3>
 * <p>
 * Pointers might be used to avoid hard references to the element to save the memory.
 * In this case the pointer stores minimal needed information to be able to restore the element when requested.
 * </p>
 *
 * <h3>Equality</h3>
 * <p>
 * It's expected that most pointers would require a read action for comparison, thus no equality is defined for pointers.
 * Pointers should be {@linkplain Pointer#dereference de-referenced} in a read action, and their values should be compared instead.
 * </p>
 *
 * @param <T> type of underlying element
 */
public interface Pointer<T> {

  /**
   * Dereferences this pointer to the current value.
   * <p>
   * Must be called under read lock and from a background thread.
   * Must not be called under write lock.
   * </p>
   * <p>
   * The returned value is expected to be valid in the current read action.
   * To use a value in another read action, create and dereference a pointer again.
   * </p>
   *
   * @return referenced value, or {@code null} if the value was invalidated or cannot be restored
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Nullable T dereference();

  /**
   * Creates a pointer which holds a strong reference to {@code value}.
   * The pointer is always dereferenced to the same object.
   * <p>
   * Use only for values that are known to be non-invalidating and safe to retain strongly.
   * </p>
   */
  @Contract(value = "_ -> new", pure = true)
  static <T> @NotNull Pointer<T> hardPointer(@NotNull T value) {
    return () -> value;
  }

  /**
   * Creates a pointer which restores its value from {@code underlyingPointer}
   * using {@code restoration}.
   * <p>
   * If the underlying value cannot be restored, this pointer dereferences to {@code null}.
   * {@code restoration} may also return {@code null}.
   * </p>
   */
  @Contract(value = "_, _ -> new", pure = true)
  static <T, U> @NotNull Pointer<T> delegatingPointer(
    @NotNull Pointer<? extends U> underlyingPointer,
    @NotNull Function<? super U, ? extends @Nullable T> restoration
  ) {
    return new DelegatingPointer.ByValue<>(underlyingPointer, restoration);
  }

  /**
   * Creates the same pointer as {@link #delegatingPointer(Pointer, Function)}, and additionally passes the created pointer to
   * {@code restoration} so the restored value may cache it.
   * <p>
   * This is useful when the restored value is a {@link Symbol} (or a similar short-lived object recreated in every read action) and wants
   * to reuse the pointer that produced it on subsequent {@link Symbol#createPointer()} calls, instead of allocating a fresh pointer each
   * time. The restored value stores the pointer in a field; the next {@code createPointer()} call short-circuits and returns the cached
   * pointer.
   * </p>
   * <p>
   * Typical usage:
   * </p>
   * <pre>{@code
   * @Override
   * public @NotNull Pointer<MySymbol> createPointer() {
   *   if (myPointer != null) return myPointer; // reuse pointer cached on a previous restore
   *   return selfDelegatingPointer(
   *     underlyingPointer,
   *     (underlyingValue, pointer) -> new MySymbol(underlyingValue, pointer)
   *   );
   * }
   * }</pre>
   */
  @Contract(value = "_, _ -> new", pure = true)
  static <T, U> @NotNull Pointer<T> selfDelegatingPointer(
    @NotNull Pointer<? extends U> underlyingPointer,
    @NotNull BiFunction<? super U, ? super Pointer<T>, ? extends @Nullable T> restoration
  ) {
    return new DelegatingPointer.ByValueAndPointer<>(underlyingPointer, restoration);
  }

  /**
   * @deprecated use {@link #selfDelegatingPointer(Pointer, BiFunction)}.
   */
  @Deprecated
  @Contract(value = "_, _ -> new", pure = true)
  static <T, U> @NotNull Pointer<T> uroborosPointer(
    @NotNull Pointer<? extends U> underlyingPointer,
    @NotNull BiFunction<? super U, ? super Pointer<T>, ? extends @Nullable T> restoration
  ) {
    return selfDelegatingPointer(underlyingPointer, restoration);
  }

  /**
   * Creates a pointer which uses {@code file} and {@code rangeInFile} to restore its value
   * with {@code restoration}.
   * <p>
   * If the file/range cannot be restored, dereferencing returns {@code null}.
   * </p>
   */
  @Contract(value = "_, _, _ -> new", pure = true)
  static <T> @NotNull Pointer<T> fileRangePointer(
    @NotNull PsiFile file,
    @NotNull TextRange rangeInFile,
    @NotNull BiFunction<? super @NotNull PsiFile, ? super @NotNull TextRange, ? extends @Nullable T> restoration
  ) {
    SmartPsiFileRange base = SmartPointerManager.getInstance(file.getProject()).createSmartPsiFileRangePointer(file, rangeInFile);
    return new FileRangePointer<>(base, restoration);
  }

}
