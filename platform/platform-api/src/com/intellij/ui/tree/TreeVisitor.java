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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;
import java.util.function.Function;

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


  abstract class Base<T> implements TreeVisitor {
    private final Function<TreePath, T> converter;

    public Base(Function<TreePath, T> converter) {
      this.converter = converter;
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      return visit(path, converter.apply(path));
    }

    @NotNull
    @SuppressWarnings("unused")
    protected Action visit(@NotNull TreePath path, T component) {
      if (component == null) return Action.SKIP_CHILDREN;
      if (matches(component)) return Action.INTERRUPT;
      if (contains(component)) return Action.CONTINUE;
      return Action.SKIP_CHILDREN;
    }

    /**
     * @param component a last component of the current path
     * @return {@code true} if the given component matches to a searching object
     */
    @SuppressWarnings("unused")
    protected boolean matches(@NotNull T component) {
      return false;
    }

    /**
     * @param component a last component of the current path
     * @return {@code true} if the given component contains a searching object
     */
    @SuppressWarnings("unused")
    protected abstract boolean contains(@NotNull T component);
  }


  abstract class ByComponent<T> extends Base<T> {
    private final T component;

    public ByComponent(@NotNull T componentToFind, Function<Object, T> converter) {
      super(converter.compose(TreePath::getLastPathComponent));
      this.component = componentToFind;
    }

    @Override
    protected boolean matches(@NotNull T component) {
      return matches(component, this.component);
    }

    @Override
    protected boolean contains(@NotNull T component) {
      return contains(component, this.component);
    }

    /**
     * @param pathComponent a last component of the current path
     * @param thisComponent a seeking component
     * @return {@code true} if both components match each other
     */
    protected boolean matches(@NotNull T pathComponent, @NotNull T thisComponent) {
      return pathComponent.equals(thisComponent);
    }

    /**
     * @param pathComponent a last component of the current path
     * @param thisComponent a seeking component
     * @return {@code true} if the first component may contain the second one
     */
    @SuppressWarnings("unused")
    protected abstract boolean contains(@NotNull T pathComponent, @NotNull T thisComponent);
  }


  class ByTreePath<T> extends Base<T> {
    private final TreePath path;

    public ByTreePath(@NotNull TreePath path, Function<Object, T> converter) {
      super(converter.compose(TreePath::getLastPathComponent));
      this.path = path;
    }

    @NotNull
    @Override
    protected Action visit(@NotNull TreePath path, T component) {
      if (component == null) return Action.SKIP_CHILDREN;

      int pathCount = path.getPathCount();
      int thisCount = this.path.getPathCount();
      if (thisCount < pathCount) return Action.SKIP_CHILDREN;

      Action action = thisCount == pathCount ? Action.INTERRUPT : Action.CONTINUE;

      TreePath value = this.path;
      while (thisCount > pathCount) {
        thisCount--;
        value = value.getParentPath();
        if (value == null) return Action.SKIP_CHILDREN;
      }
      return matches(component, value.getLastPathComponent()) ? action : Action.SKIP_CHILDREN;
    }

    /**
     * @param pathComponent a last component of the current path
     * @param thisComponent a component of the seeking path at the same level
     * @return {@code true} if both components match each other
     */
    protected boolean matches(@NotNull T pathComponent, @NotNull Object thisComponent) {
      return pathComponent.equals(thisComponent);
    }

    @Override
    protected final boolean matches(@NotNull T component) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected final boolean contains(@NotNull T component) {
      throw new UnsupportedOperationException();
    }
  }
}
