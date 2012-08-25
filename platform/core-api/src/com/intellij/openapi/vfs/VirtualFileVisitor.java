/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 * @since 31.10.2011
 */
public abstract class VirtualFileVisitor {
  public static class Option {
    private Option() { }

    private static class LimitOption extends Option {
      private final int limit;

      private LimitOption(int limit) {
        this.limit = limit;
      }
    }
  }

  public static final Option NO_FOLLOW_SYMLINKS = new Option();
  public static final Option SKIP_ROOT = new Option();
  public static final Option ONE_LEVEL_DEEP = new Option.LimitOption(1);

  public static Option limit(int maxDepth) {
    return new Option.LimitOption(maxDepth);
  }


  public static class Result {
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
    public VisitorException(Throwable cause) {
      super(cause);
    }
  }


  private boolean myFollowSymLinks = true;
  private boolean mySkipRoot = false;
  private int myDepthLimit = -1;

  private int myLevel = -1;
  private Stack<Map<Key, Object>> myParameters = null;

  protected VirtualFileVisitor(Option... options) {
    for (Option option : options) {
      if (option == NO_FOLLOW_SYMLINKS) myFollowSymLinks = false;
      else if (option == SKIP_ROOT) mySkipRoot = true;
      else if (option instanceof Option.LimitOption) myDepthLimit = ((Option.LimitOption)option).limit;
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
  @NotNull
  public Result visitFileEx(@NotNull VirtualFile file) {
    return visitFile(file) ? CONTINUE : SKIP_CHILDREN;
  }

  /**
   * This method is only called if visiting wasn't interrupted (by returning skip-requesting result
   * from {@linkplain #visitFile(VirtualFile)} or {@linkplain #visitFileEx(VirtualFile)} methods).
   *
   * @param file a file whose children were successfully visited.
   */
  public void afterChildrenVisited(@NotNull VirtualFile file) { }

  /**
   * By default, visitor uses ({@linkplain com.intellij.openapi.vfs.VirtualFile#getChildren()}) to iterate over file's children.
   * You can override this method to implement another mechanism.
   *
   * @param file a virtual file to get children from.
   * @return children iterable, or null to use {@linkplain com.intellij.openapi.vfs.VirtualFile#getChildren()}.
   */
  @Nullable
  public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
    return null;
  }


  public final <T> void set(@NotNull Key<T> parameter, @Nullable T value) {
    if (myParameters == null) {
      myParameters = ContainerUtil.newStack();
    }

    final Map<Key, Object> frame;
    if (myParameters.isEmpty()) {
      myParameters.push(frame = ContainerUtil.newHashMap());
    }
    else {
      frame = myParameters.peek();
    }

    frame.put(parameter, value);
  }

  @SuppressWarnings("ConstantConditions")
  public final <T> T get(@NotNull Key<T> parameter) {
    if (myParameters == null || myParameters.isEmpty()) {
      return null;
    }
    @SuppressWarnings({"unchecked", "UnnecessaryLocalVariable"}) final T value = (T)myParameters.peek().get(parameter);
    return value;
  }


  final boolean allowVisitFile(@SuppressWarnings("UnusedParameters") @NotNull VirtualFile file) {
    return myLevel > 0 || !mySkipRoot;
  }

  final boolean allowVisitChildren(@NotNull VirtualFile file) {
    return !file.isSymLink() || (myFollowSymLinks && !VfsUtilCore.isInvalidLink(file));
  }

  final boolean depthLimitReached() {
    return myDepthLimit >= 0 && myLevel >= myDepthLimit;
  }

  final void pushFrame() {
    ++myLevel;

    if (myParameters != null && !myParameters.isEmpty()) {
      final Map<Key, Object> lastFrame = myParameters.peek();
      myParameters.push(new HashMap<Key, Object>() {
        @Override
        public Object get(Object key) {
          return containsKey(key) ? super.get(key) : lastFrame.get(key);
        }
      });
    }
  }

  final void popFrame() {
    --myLevel;

    if (myParameters != null && !myParameters.isEmpty()) {
      myParameters.pop();
    }
  }
}
