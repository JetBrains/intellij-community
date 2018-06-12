// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.util;

import com.intellij.lang.jvm.JvmClass;
import com.intellij.openapi.progress.ProgressManager;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.lang.jvm.util.JvmUtil.resolveClass;
import static com.intellij.lang.jvm.util.JvmUtil.resolveClasses;

public class JvmHierarchyUtil {

  private JvmHierarchyUtil() {}

  public static boolean testSupers(@NotNull JvmClass start, boolean skipStart, @NotNull Predicate<JvmClass> predicate) {
    Boolean result = traverseSupers(start, skipStart, it -> predicate.test(it) ? Boolean.TRUE : null);
    return result != null && result;
  }

  public static <R> R traverseSupers(@NotNull JvmClass start, @NotNull Function<? super JvmClass, R> f) {
    return traverseSupers(start, false, f);
  }

  /**
   * Traverses class tree in BFS order applying the function to each superclass.
   * <p/>
   * Notes:
   * <ul>
   * <li><i>supers</i> term includes both classes and interfaces</li>
   * <li>the function will also be applied to the start class</li>
   * <li>if the function returns non-null result for any class then traversal is stopped and result is returned</li>
   * <li>the function is applied to each class at most once</li>
   * <li>unresolvable supertypes are skipped</li>
   * </ul>
   *
   * @param start class to start traversal from
   * @param <R>   type of the result
   * @return first non-null result or null
   */
  public static <R> R traverseSupers(@NotNull JvmClass start, boolean skipStart, @NotNull Function<? super JvmClass, R> f) {
    // TODO implement method returning Stream<JvmClass>
    final Queue<JvmClass> queue = new ArrayDeque<>();
    if (skipStart) {
      queueSupers(queue, start);
    }
    else {
      queue.offer(start);
    }

    final Set<JvmClass> visited = new THashSet<>();
    while (!queue.isEmpty()) {
      ProgressManager.checkCanceled();

      JvmClass current = queue.remove();
      if (!visited.add(current)) continue;

      R result = f.apply(current);
      if (result != null) return result;

      queueSupers(queue, current);
    }

    return null;
  }

  private static void queueSupers(@NotNull Queue<JvmClass> queue, @NotNull JvmClass current) {
    JvmClass superClass = resolveClass(current.getSuperClassType());
    if (superClass != null) {
      queue.offer(superClass);
    }
    for (JvmClass anInterface : resolveClasses(current.getInterfaceTypes())) {
      queue.offer(anInterface);
    }
  }
}
