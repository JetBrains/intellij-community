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
package com.intellij.util;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;

@SuppressWarnings("unused")
public class AstLoadingFilter {

  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Set<VirtualFile>> myEnabledFiles = ThreadLocal.withInitial(() -> new HashSet<>());
  private static final ThreadLocal<Integer> myEnabledCounter = ThreadLocal.withInitial(() -> 0);

  public static boolean isTreeLoadingEnabled(@NotNull VirtualFile file) {
    if (!Registry.is("disable.tree.loading")) return true;
    return file instanceof VirtualFileWindow || myEnabledCounter.get() > 0 || myEnabledFiles.get().contains(file);
  }

  public static <E extends Throwable>
  void enableTreeLoading(@NotNull ThrowableRunnable<E> runnable) throws E {
    try {
      increment(myEnabledCounter);
      runnable.run();
    }
    finally {
      decrement(myEnabledCounter);
    }
  }

  public static <T, E extends Throwable>
  T enableTreeLoading(@NotNull ThrowableComputable<T, E> computable) throws E {
    try {
      increment(myEnabledCounter);
      return computable.compute();
    }
    finally {
      decrement(myEnabledCounter);
    }
  }

  public static <E extends Throwable>
  void enableTreeLoading(@Nullable PsiElement element, @NotNull ThrowableRunnable<E> runnable) throws E {
    enableTreeLoading(getVirtualFile(element), runnable);
  }

  public static <T, E extends Throwable>
  T enableTreeLoading(@Nullable PsiElement element, @NotNull ThrowableComputable<T, E> computable) throws E {
    return enableTreeLoading(getVirtualFile(element), computable);
  }

  public static <E extends Throwable>
  void enableTreeLoading(@Nullable VirtualFile file, @NotNull ThrowableRunnable<E> runnable) throws E {
    if (file != null && myEnabledFiles.get().add(file)) {
      try {
        runnable.run();
      }
      finally {
        myEnabledFiles.get().remove(file);
      }
    }
    else {
      runnable.run();
    }
  }

  public static <T, E extends Throwable>
  T enableTreeLoading(@Nullable VirtualFile file, @NotNull ThrowableComputable<T, E> computable) throws E {
    if (file != null && myEnabledFiles.get().add(file)) {
      try {
        return computable.compute();
      }
      finally {
        myEnabledFiles.get().remove(file);
      }
    }
    else {
      return computable.compute();
    }
  }

  private static void increment(@NotNull ThreadLocal<Integer> counter) {
    counter.set(counter.get() + 1);
  }

  private static void decrement(@NotNull ThreadLocal<Integer> counter) {
    counter.set(counter.get() - 1);
  }
}
