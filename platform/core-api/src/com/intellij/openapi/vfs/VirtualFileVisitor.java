// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 *
 * @see VfsUtilCore#visitChildrenRecursively
 * @see VfsUtilCore#iterateChildrenRecursively
 * @see VfsUtilCore#processFilesRecursively
 */
public abstract class VirtualFileVisitor<T> {
  private boolean myFollowSymLinks = true;
  private boolean mySkipRoot;
  private int myDepthLimit = -1;

  private int myLevel;
  private Stack<T> myValueStack;
  private T myValue;

  protected VirtualFileVisitor(Option @NotNull ... options) {
    for (Option option : options) {
      if (option == NO_FOLLOW_SYMLINKS) {
        myFollowSymLinks = false;
      }
      else if (option == SKIP_ROOT) {
        mySkipRoot = true;
      }
      else if (option instanceof Option.LimitOption) {
        myDepthLimit = ((Option.LimitOption)option).limit;
      }
    }
  }

  public static class Option {
    private Option() { }

    private static final class LimitOption extends Option {
      private final int limit;

      private LimitOption(int limit) {
        this.limit = limit;
      }
    }
  }

  public static final Option NO_FOLLOW_SYMLINKS = new Option();
  public static final Option SKIP_ROOT = new Option();
  public static final Option ONE_LEVEL_DEEP = limit(1);

  public static @NotNull Option limit(int maxDepth) {
    return new Option.LimitOption(maxDepth);
  }

  public static final class Result {
    public final boolean skipChildren;
    public final VirtualFile skipToParent;

    private Result(boolean skipChildren, @Nullable VirtualFile skipToParent) {
      this.skipChildren = skipChildren;
      this.skipToParent = skipToParent;
    }

    @Override
    public String toString() {
      return "(" + (skipChildren ? "skip," + skipToParent : "continue") + ")";
    }
  }

  public static final Result CONTINUE = new Result(false, null);
  public static final Result SKIP_CHILDREN = new Result(true, null);

  public static Result skipTo(@NotNull VirtualFile parentToSkipTo) {
    return new Result(true, parentToSkipTo);
  }

  protected static class VisitorException extends RuntimeException {
    public VisitorException(@NotNull Throwable cause) {
      super(cause);
    }
  }

  /**
   * Simple visiting method.
   * On returning {@code true} a visitor will proceed to file's children, on {@code false} - to file's next sibling.
   *
   * @param file a file to visit.
   * @return {@code true} to proceed to file's children, {@code false} to skip to file's next sibling.
   */
  public boolean visitFile(@NotNull VirtualFile file) {
    return true;
  }

  /**
   * Extended visiting method.
   *
   * @param file a file to visit.
   * @return {@linkplain #CONTINUE} to proceed to file's children,<br/>
   *         {@linkplain #SKIP_CHILDREN} to skip to file's next sibling,<br/>
   *         result of {@linkplain #skipTo(VirtualFile)} to skip to given file's next sibling.
   */
  public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
    return visitFile(file) ? CONTINUE : SKIP_CHILDREN;
  }

  /**
   * This method is only called if visiting wasn't interrupted (by returning a skip-requesting result
   * from {@linkplain #visitFile(VirtualFile)} or {@linkplain #visitFileEx(VirtualFile)} methods).
   *
   * @param file a file whose children were successfully visited.
   */
  public void afterChildrenVisited(@NotNull VirtualFile file) { }

  /**
   * By default, a visitor uses ({@linkplain VirtualFile#getChildren()}) to iterate over file's children.
   * You can override this method to implement another mechanism.
   *
   * @param file a virtual file to get children from.
   * @return children iterable, or null to use {@linkplain VirtualFile#getChildren()}.
   */
  public @Nullable Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
    return null;
  }

  /**
   * Stores the {@code value} to this visitor. The stored value can be retrieved later by calling the {@link #getCurrentValue()}.
   * The visitor maintains the stack of stored values. I.e:
   * This value is held here only during visiting the current file and all its children. As soon as the visitor finished with
   * the current file and all its subtree and returns to the level up, the value is cleared
   * and the {@link #getCurrentValue()} returns the previous value, which was stored here before this method call.
   */
  public final void setValueForChildren(@Nullable T value) {
    myValue = value;
    if (myValueStack == null) {
      myValueStack = new Stack<>();
    }
  }

  public final T getCurrentValue() {
    return myValue;
  }

  final boolean allowVisitFile(@SuppressWarnings("UnusedParameters") @NotNull VirtualFile file) {
    return myLevel > 0 || !mySkipRoot;
  }

  final boolean allowVisitChildren(@NotNull VirtualFile file) {
    if (!file.is(VFileProperty.SYMLINK)) {
      return true;
    }

    if (!myFollowSymLinks) {
      return false;
    }

    // ignoring invalid and recursive symbolic links - to avoid visiting files twice
    return !file.isRecursiveOrCircularSymlink();
  }

  final boolean depthLimitReached() {
    return myDepthLimit >= 0 && myLevel >= myDepthLimit;
  }

  final void saveValue() {
    ++myLevel;
    if (myValueStack != null) {
      myValueStack.push(myValue);
    }
  }

  final void restoreValue(boolean pushed) {
    if (pushed) {
      --myLevel;
      if (myValueStack != null && !myValueStack.isEmpty()) {
        myValueStack.pop();
      }
    }

    if (myValueStack != null) {
      myValue = myValueStack.isEmpty() ? null : myValueStack.peek();
    }
  }
}
