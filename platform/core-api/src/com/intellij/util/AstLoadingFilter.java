// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Enforces tree loading policies.
 * <p/>
 * For example it's very slow to load AST for all shown files
 * when updating Project View nodes,
 * or loading other files when current file is being highlighted.
 * <p/>
 * To prevent loading <i>in the current thread</i> {@link #disableTreeLoading} should be used.<br/>
 * In this case the exception will be thrown when some code unexpectedly tries to load the tree, and then there are two options:
 * <ul>
 * <li>examine the stack trace, fix the code,
 * so tree loading won't occur anymore,
 * e.g. data will be taken from stubs,
 * and cover it with a test.
 * <b>It is highly preferable to fix the code</b>, which gains a speed up.
 * </li>
 * <li>force enable tree loading by wrapping troublesome operation into {@link #forceEnableTreeLoading}.
 * In this case there are no performance gains, but later it will be possible to examine all bottlenecks
 * by finding usages of {@link #forceEnableTreeLoading}</li>
 * </ul>
 * Example:
 * <pre>
 *   disableTreeLoading {
 *     // some deep trace
 *     doSomeOperation {
 *       // access tree, exception is thrown
 *     }
 *     ...
 *     forceEnableTreeLoading {
 *       doSomeOperation {
 *         // access tree, no exception is thrown
 *       }
 *     }
 *   }
 * </pre>
 * <p/>
 * Note that tree access won't result in an exception when the tree was already loaded.
 */
public class AstLoadingFilter {

  private static final ThreadLocal<Boolean> myDisabledSwitch = ThreadLocal.withInitial(() -> false); // enable by default
  private static final ThreadLocal<Boolean> myForcedEnabled = ThreadLocal.withInitial(() -> false);

  private AstLoadingFilter() {}

  public static boolean isTreeLoadingEnabled(@NotNull VirtualFile file) {
    if (!Registry.is("ast.loading.filter")) return true;
    if (file instanceof VirtualFileWindow) return true;
    return !myDisabledSwitch.get() || myForcedEnabled.get();
  }

  public static <E extends Throwable>
  void disableTreeLoading(@NotNull ThrowableRunnable<E> runnable) throws E {
    disableTreeLoading(() -> {
      runnable.run();
      return null;
    });
  }

  public static <T, E extends Throwable>
  T disableTreeLoading(@NotNull ThrowableComputable<T, E> computable) throws E {
    return computeWithSwitch(computable, myDisabledSwitch);
  }

  public static <E extends Throwable>
  void forceEnableTreeLoading(@NotNull ThrowableRunnable<E> runnable) throws E {
    forceEnableTreeLoading(() -> {
      runnable.run();
      return null;
    });
  }

  public static <T, E extends Throwable>
  T forceEnableTreeLoading(@NotNull ThrowableComputable<T, E> computable) throws E {
    if (!myDisabledSwitch.get()) {
      throw new IllegalStateException("It's not allowed to force enable loading before it has been disabled");
    }
    return computeWithSwitch(computable, myForcedEnabled);
  }

  private static <T, E extends Throwable>
  T computeWithSwitch(@NotNull ThrowableComputable<T, E> computable, @NotNull ThreadLocal<Boolean> theSwitch) throws E {
    if (theSwitch.get()) {
      return computable.compute();    // switch is already on
    }
    else {
      try {
        theSwitch.set(true);          // switch on
        return computable.compute();
      }
      finally {
        theSwitch.set(false);         // reset switch
      }
    }
  }
}
