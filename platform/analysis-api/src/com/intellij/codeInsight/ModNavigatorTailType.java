// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ModNavigator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A base class for tail types that use {@link ModNavigator} instead of {@link Editor} to insert the tail.
 */
@ApiStatus.Experimental
public abstract class ModNavigatorTailType extends TailType {
  /**
   * @return true. {@link ModNavigatorTailType} should be always applicable. 
   * @deprecated If you want to make it non-applicable, simply do nothing inside {@link #processTail(ModNavigator, int)}.
   * May become final in future.
   */
  @Deprecated
  @Override
  public boolean isApplicable(@NotNull InsertionContext context) {
    return true;
  }

  /**
   * @implSpec this implementation delegates to {@link #processTail(ModNavigator, int)} adapting the arguments.
   * Normally, it should not be overridden in clients.
   */
  @Override
  public int processTail(final @NotNull Editor editor, int tailOffset) {
    return processTail(editor.asModNavigator(), tailOffset);
  }

  /**
   * @param navigator  {@link ModNavigator} to use
   * @param tailOffset tail offset
   * @return new tail offset
   */
  public abstract int processTail(@NotNull ModNavigator navigator, int tailOffset);

  /**
   * @return tail type that does nothing
   */
  @Contract(pure = true)
  public static @NotNull ModNavigatorTailType noneType() {
    return (ModNavigatorTailType)TailTypes.noneType();
  }

  /**
   * @return tail type that inserts a semicolon
   */
  @Contract(pure = true)
  public static @NotNull ModNavigatorTailType semicolonType() {
    return (ModNavigatorTailType)TailTypes.semicolonType();
  }

  /**
   * @return tail type that always inserts a space
   */
  @Contract(pure = true)
  public static @NotNull ModNavigatorTailType insertSpaceType() {
    return (ModNavigatorTailType)TailTypes.insertSpaceType();
  }

  /**
   * @return tail type that inserts a space, overtyping an existing one
   */
  @Contract(pure = true)
  public static @NotNull ModNavigatorTailType spaceType() {
    return (ModNavigatorTailType)TailTypes.spaceType();
  }

  /**
   * @return tail type that inserts a dot
   */
  @Contract(pure = true)
  public static @NotNull ModNavigatorTailType dotType() {
    return (ModNavigatorTailType)TailTypes.dotType();
  }
  

  /**
   * @return tail type that insert a space unless there's one at the caret position already, followed by a word or '@'
   */
  @Contract(pure = true)
  public static @NotNull ModNavigatorTailType humbleSpaceBeforeWordType() {
    return (ModNavigatorTailType)TailTypes.humbleSpaceBeforeWordType();
  }
}
