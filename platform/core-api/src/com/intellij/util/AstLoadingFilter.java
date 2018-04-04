// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Enforces tree loading policies.
 * <p/>
 * For example it's very slow to load AST for all shown files when updating Project View nodes,
 * or loading other files when current file is being highlighted.
 * <p/>
 * To prevent loading <i>in the current thread</i> {@link #disableTreeLoading} should be used.<br/>
 * In this case the exception will be thrown when some code unexpectedly tries to load the tree,
 * and then there are two options:
 * <ul>
 * <li>examine the stack trace, fix the code, so tree loading won't occur anymore,
 * e.g. data will be taken from stubs, and cover it with a test.
 * <b>It is highly preferable to fix the code</b>, which gains a speed up.
 * </li>
 * <li>force enable tree loading by wrapping troublesome operation into {@link #forceEnableTreeLoading}.
 * In this case there are no performance gains, but later it will be possible to examine all bottlenecks
 * by finding usages of {@link #forceEnableTreeLoading}</li>
 * </ul>
 * Example:
 * <pre>
 * disableTreeLoading {
 *   // some deep trace
 *   ...
 *   doSomeOperation {
 *     // access tree, exception is thrown
 *   }
 *   forceEnableTreeLoading {
 *     doSomeOperation {
 *       // access tree, no exception is thrown
 *     }
 *   }
 *   forceEnableTreeLoading {
 *     disableTreeLoading {
 *       doSomeOperation {
 *         // nested disabling has no effect in any case
 *         // access tree, no exception is thrown, access is still enabled
 *       }
 *     }
 *   }
 * }
 * </pre>
 * <p/>
 * Note that tree access won't result in an exception when the tree was already loaded.
 */
public class AstLoadingFilter {

  private static final Logger LOG = Logger.getInstance(AstLoadingFilter.class);
  /**
   * Holds not-null value if loading was disabled in current thread.
   * Initial value is {@code null} meaning loading is enabled by default.
   */
  private static final ThreadLocal<Supplier<String>> myDisabledInfo = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> myForcedEnabled = ThreadLocal.withInitial(() -> false);

  private AstLoadingFilter() {}

  public static void assertTreeLoadingEnabled(@NotNull VirtualFile file) {
    if (!Registry.is("ast.loading.filter")) return;
    if (file instanceof VirtualFileWindow) return;
    Supplier<String> disabledInfo = myDisabledInfo.get();
    if (disabledInfo == null) {
      // loading was not disabled in current thread
    }
    else if (myForcedEnabled.get()) {
      // loading was disabled but then re-enabled
    }
    else {
      String debugInfo = disabledInfo.get();
      String message = "Tree access disabled";
      Attachment filePathAttachment = new Attachment(file.getPath(), "");
      if (debugInfo == null) {
        LOG.error(message, filePathAttachment);
      }
      else {
        LOG.error(message, filePathAttachment, new Attachment("debugInfo", debugInfo));
      }
    }
  }

  public static <E extends Throwable>
  void disableTreeLoading(@NotNull ThrowableRunnable<E> runnable) throws E {
    disableTreeLoading(toComputable(runnable));
  }

  public static <T, E extends Throwable>
  T disableTreeLoading(@NotNull ThrowableComputable<T, E> computable) throws E {
    return disableTreeLoading(computable, () -> null);
  }

  public static <T, E extends Throwable>
  T disableTreeLoading(@NotNull ThrowableComputable<T, E> computable, @NotNull Supplier<String> debugInfo) throws E {
    if (myDisabledInfo.get() != null) {
      return computable.compute();
    }
    else {
      try {
        myDisabledInfo.set(debugInfo);
        return computable.compute();
      }
      finally {
        myDisabledInfo.set(null);
      }
    }
  }

  public static <E extends Throwable>
  void forceEnableTreeLoading(@NotNull ThrowableRunnable<E> runnable) throws E {
    forceEnableTreeLoading(toComputable(runnable));
  }

  public static <T, E extends Throwable>
  T forceEnableTreeLoading(@NotNull ThrowableComputable<T, E> computable) throws E {
    if (myDisabledInfo.get() == null) {
      throw new IllegalStateException("It's not allowed to force enable loading before it has been disabled");
    }
    if (myForcedEnabled.get()) {
      return computable.compute();
    }
    else {
      try {
        myForcedEnabled.set(true);
        return computable.compute();
      }
      finally {
        myForcedEnabled.set(false);
      }
    }
  }

  private static <E extends Throwable> ThrowableComputable<?, E> toComputable(ThrowableRunnable<E> runnable) {
    return () -> {
      runnable.run();
      return null;
    };
  }
}
