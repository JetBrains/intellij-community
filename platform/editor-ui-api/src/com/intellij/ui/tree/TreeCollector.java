// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;

import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.util.ArrayUtil.isEmpty;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

public final class TreeCollector<T> {
  private final AtomicReference<List<T>> reference = new AtomicReference<>();
  private final BiPredicate<? super T, ? super T> predicate;

  private TreeCollector(@NotNull BiPredicate<? super T, ? super T> predicate) {
    this.predicate = predicate;
  }

  public @NotNull List<T> get() {
    synchronized (reference) {
      List<T> list = reference.getAndSet(null);
      return list != null ? list : emptyList();
    }
  }

  public boolean add(@NotNull T object) {
    synchronized (reference) {
      List<T> list = reference.get();
      if (list != null) return add(predicate, list, object);
      reference.set(new SmartList<>(object));
      return true;
    }
  }

  private static @NotNull <T> List<T> collect(@NotNull BiPredicate<? super T, ? super T> predicate, T... objects) {
    return isEmpty(objects) ? new ArrayList<>() : collect(predicate, asList(objects));
  }

  private static @NotNull <T> List<T> collect(@NotNull BiPredicate<? super T, ? super T> predicate, @NotNull Collection<? extends T> objects) {
    List<T> list = new ArrayList<>(objects.size());
    for (T object : objects) if (object != null) add(predicate, list, object);
    return list;
  }

  private static <T> boolean add(@NotNull BiPredicate<? super T, ? super T> predicate, @NotNull List<T> list, @NotNull T object) {
    for (T each : list) if (predicate.test(each, object)) return false;
    list.removeIf(each -> predicate.test(object, each));
    list.add(object);
    return true;
  }


  public static final class VirtualFileLeafs {
    private static final BiPredicate<VirtualFile, VirtualFile> PREDICATE = (child, parent) -> isAncestor(parent, child, false);

    public static @NotNull TreeCollector<VirtualFile> create() {
      return new TreeCollector<>(PREDICATE);
    }

    public static @NotNull List<VirtualFile> collect(VirtualFile... files) {
      return TreeCollector.collect(PREDICATE, files);
    }

    public static @NotNull List<VirtualFile> collect(@NotNull Collection<? extends VirtualFile> files) {
      return TreeCollector.collect(PREDICATE, files);
    }
  }


  public static final class VirtualFileRoots {
    private static final BiPredicate<VirtualFile, VirtualFile> PREDICATE = (parent, child) -> isAncestor(parent, child, false);

    public static @NotNull TreeCollector<VirtualFile> create() {
      return new TreeCollector<>(PREDICATE);
    }

    public static @NotNull List<VirtualFile> collect(VirtualFile... files) {
      return TreeCollector.collect(PREDICATE, files);
    }

    public static @NotNull List<VirtualFile> collect(@NotNull Collection<? extends VirtualFile> files) {
      return TreeCollector.collect(PREDICATE, files);
    }
  }


  public static final class TreePathLeafs {
    private static final BiPredicate<TreePath, TreePath> PREDICATE = (child, parent) -> parent.isDescendant(child);

    public static @NotNull TreeCollector<TreePath> create() {
      return new TreeCollector<>(PREDICATE);
    }

    public static @NotNull List<TreePath> collect(TreePath... paths) {
      return TreeCollector.collect(PREDICATE, paths);
    }

    public static @NotNull List<TreePath> collect(@NotNull Collection<? extends TreePath> paths) {
      return TreeCollector.collect(PREDICATE, paths);
    }
  }


  public static final class TreePathRoots {
    private static final BiPredicate<TreePath, TreePath> PREDICATE = (parent, child) -> parent.isDescendant(child);

    public static @NotNull TreeCollector<TreePath> create() {
      return new TreeCollector<>(PREDICATE);
    }

    public static @NotNull List<TreePath> collect(TreePath... paths) {
      return TreeCollector.collect(PREDICATE, paths);
    }

    public static @NotNull List<TreePath> collect(@NotNull Collection<? extends TreePath> paths) {
      return TreeCollector.collect(PREDICATE, paths);
    }
  }
}
