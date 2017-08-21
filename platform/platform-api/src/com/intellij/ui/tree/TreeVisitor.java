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

import javax.swing.tree.TreePath;
import java.util.function.Function;

public interface TreeVisitor {
  /**
   * @param path a currently visited path
   * @return an action that controls visiting a tree
   */
  @NotNull
  Action accept(@NotNull TreePath path);

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


  abstract class Finder implements TreeVisitor {
    @NotNull
    @Override
    public Action accept(@NotNull TreePath path) {
      return found(path) ? Action.INTERRUPT : contains(path) ? Action.CONTINUE : Action.SKIP_CHILDREN;
    }

    /**
     * @param path a currently visited path
     * @return {@code true} if the specified path is found and visiting can be interrupted
     */
    protected abstract boolean found(@NotNull TreePath path);

    /**
     * @param path a currently visited path
     * @return {@code true} if the specified path may contain a seeking path
     */
    protected abstract boolean contains(@NotNull TreePath path);
  }


  class PathFinder implements TreeVisitor {
    private final Function<Object, Object> converter;
    private final TreePath path;

    public PathFinder(@NotNull TreePath path) {
      this(path, object -> object);
    }

    public PathFinder(@NotNull TreePath path, @NotNull Function<Object, Object> converter) {
      this.converter = converter;
      this.path = path;
    }

    @NotNull
    @Override
    public Action accept(@NotNull TreePath path) {
      Object component = converter.apply(path.getLastPathComponent());
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
    protected boolean matches(@NotNull Object pathComponent, @NotNull Object thisComponent) {
      return pathComponent.equals(thisComponent);
    }
  }
}
