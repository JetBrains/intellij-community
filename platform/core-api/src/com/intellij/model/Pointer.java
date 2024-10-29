// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Experimental;
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
 * <pre>
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
 * </pre>
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
@Experimental
public interface Pointer<T> {

  /**
   *
   * Note: should not be called under write lock.
   * Instead, you shall be using {@link com.intellij.openapi.application.CoroutinesKt#readAndWriteAction}, deference the pointer under
   * a read lock and pass dereferenced symbol to the write action directly with a hard reference.
   *
   * @return referenced value, or {@code null} if the value was invalidated or cannot be restored
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Nullable T dereference();

  /**
   * Creates a pointer which holds the strong reference to the {@code value}.
   * The pointer is always de-referenced into the passed {@code value}.
   * Hard pointers should be used only for values that cannot be invalidated.
   */
  @Contract(value = "_ -> new", pure = true)
  static <T> @NotNull Pointer<T> hardPointer(@NotNull T value) {
    return () -> value;
  }

  /**
   * Creates a pointer which uses {@code underlyingPointer} value to restore its value with {@code restoration} function.
   */
  @Contract(value = "_, _ -> new", pure = true)
  static <T, U> @NotNull Pointer<T> delegatingPointer(
    @NotNull Pointer<? extends U> underlyingPointer,
    @NotNull Function<? super U, ? extends T> restoration
  ) {
    return new DelegatingPointer.ByValue<>(underlyingPointer, restoration);
  }

  /**
   * Creates the same pointer as {@link #delegatingPointer}, which additionally passes itself
   * into the {@code restoration} function to allow caching the pointer in the restored value.
   */
  @Contract(value = "_, _ -> new", pure = true)
  static <T, U> @NotNull Pointer<T> uroborosPointer(
    @NotNull Pointer<? extends U> underlyingPointer,
    @NotNull BiFunction<? super U, ? super Pointer<T>, ? extends T> restoration
  ) {
    return new DelegatingPointer.ByValueAndPointer<>(underlyingPointer, restoration);
  }

  /**
   * Creates a pointer which uses {@code file} and {@code rangeInFile} to restore its value with {@code restoration} function.
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

  /**
   * Creates a pointer which uses {@code underlyingPointer} value to restore its value with {@code restoration} function.
   * <p/>
   * Equality of {@code restoration} function is unreliable, because it might be a lambda.
   * The {@code key} must be passed to check for equality instead,
   * where two equal keys mean the same restoration logic will be applied.
   *
   * @deprecated use {@link #delegatingPointer(Pointer, Function)}.
   * This method is deprecated because the pointer equality was intended to be used without the read action,
   * while often being impossible to implement without it, which makes it infeasible to use on the EDT.
   */
  @ApiStatus.Internal
  @Deprecated
  @Contract(value = "_, _, _ -> new", pure = true)
  static <T, U> @NotNull Pointer<T> delegatingPointer(@NotNull Pointer<? extends U> underlyingPointer,
                                                      @NotNull Object key,
                                                      @NotNull Function<? super U, ? extends T> restoration) {
    return new DelegatingPointerEq.ByValue<>(underlyingPointer, key, restoration);
  }
}
