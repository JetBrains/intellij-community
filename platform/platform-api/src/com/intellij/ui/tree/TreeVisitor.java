/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.tree;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;

public interface TreeVisitor {
  /**
   * @param path a currently visited path
   * @return an action that controls visiting a tree
   */
  @NotNull
  Action visit(@NotNull TreePath path);

  enum Action {
    /**
     * Interrupt visiting a tree structure.
     */
    INTERRUPT,
    /**
     * Continue visiting the node children.
     */
    CONTINUE,
    /**
     * Continue without visiting the node children.
     */
    SKIP_CHILDREN,
    /**
     * Continue without visiting the node siblings.
     */
    SKIP_SIBLINGS,
  }


  /**
   * Represents a tree model that accepts a tree visitor and promises a result.
   */
  interface Acceptor {
    /**
     * @param visitor an object that controls visiting a tree structure
     * @return a promise that will be resolved when visiting is finished
     */
    @NotNull
    Promise<TreePath> accept(@NotNull TreeVisitor visitor);
  }


  abstract class ByComponent<C, T> implements TreeVisitor {
    private final Function<TreePath, T> converter;
    private final C component;

    public ByComponent(@NotNull C component, @NotNull Function<Object, T> converter) {
      this.converter = currentPath -> converter.fun(currentPath.getLastPathComponent());
      this.component = component;
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      return visit(converter.fun(path));
    }

    /**
     * @param component a last component of the current path
     * @return an action that controls visiting a tree
     */
    @NotNull
    protected Action visit(T component) {
      if (component == null) return Action.SKIP_CHILDREN;
      if (matches(component, this.component)) return Action.INTERRUPT;
      if (contains(component, this.component)) return Action.CONTINUE;
      return Action.SKIP_CHILDREN;
    }

    /**
     * @param pathComponent a last component of the current path
     * @param thisComponent a seeking component
     * @return {@code true} if both components match each other
     */
    protected boolean matches(@NotNull T pathComponent, @NotNull C thisComponent) {
      return pathComponent.equals(thisComponent);
    }

    /**
     * @param pathComponent a last component of the current path
     * @param thisComponent a seeking component
     * @return {@code true} if the first component may contain the second one
     */
    protected abstract boolean contains(@NotNull T pathComponent, @NotNull C thisComponent);
  }


  class ByTreePath<T> implements TreeVisitor {
    private final Function<TreePath, T> converter;
    private final boolean ignoreRoot;
    private final TreePath path;
    private final int count;

    public ByTreePath(@NotNull TreePath path, @NotNull Function<Object, T> converter) {
      this(false, path, converter);
    }

    public ByTreePath(boolean ignoreRoot, @NotNull TreePath path, @NotNull Function<Object, T> converter) {
      this.converter = currentPath -> converter.fun(currentPath.getLastPathComponent());
      this.ignoreRoot = ignoreRoot;
      this.path = path;
      this.count = ignoreRoot
                   ? path.getPathCount() + 1
                   : path.getPathCount();
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      return ignoreRoot && null == path.getParentPath() ? Action.CONTINUE : visit(path, converter.fun(path));
    }

    /**
     * @param path      a currently visited path
     * @param component a corresponding component
     * @return an action that controls visiting a tree
     */
    @NotNull
    protected Action visit(@NotNull TreePath path, T component) {
      if (component == null) return Action.SKIP_CHILDREN;
      int count = path.getPathCount();
      if (count < this.count) {
        TreePath parent = this.path.getParentPath();
        while (++count < this.count && parent != null) parent = parent.getParentPath();
        boolean found = parent != null && matches(component, parent.getLastPathComponent());
        return !found ? Action.SKIP_CHILDREN : Action.CONTINUE;
      }
      else {
        boolean found = count > this.count || matches(component, this.path.getLastPathComponent());
        return !found ? Action.SKIP_CHILDREN : visit(path, component, count - this.count);
      }
    }

    /**
     * @param path      a currently visited path
     * @param component a corresponding component
     * @param depth     a depth starting from the found node
     * @return an action that controls visiting a tree
     */
    @NotNull
    @SuppressWarnings("unused")
    protected Action visit(@NotNull TreePath path, @NotNull T component, int depth) {
      return depth == 0 ? Action.INTERRUPT : Action.SKIP_CHILDREN;
    }

    /**
     * @param pathComponent a last component of the current path
     * @param thisComponent a component of the seeking path at the same level
     * @return {@code true} if both components match each other
     */
    protected boolean matches(@NotNull T pathComponent, @NotNull Object thisComponent) {
      return pathComponent.equals(thisComponent);
    }
  }
}
