// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
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
 *     // access file tree, exception is thrown
 *   }
 *   forceEnableTreeLoading(file) {
 *     doSomeOperation {
 *       // access file tree, no exception is thrown
 *     }
 *     doAnotherOperation {
 *       // access another file tree, exception is thrown
 *     }
 *   }
 *   forceEnableTreeLoading(file) {
 *     disableTreeLoading {
 *       doSomeOperation {
 *         // nested disabling has no effect in any case
 *         // access file tree, no exception is thrown, access is still enabled for file
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
  private static final Set<String> ourReportedTraces = ContainerUtil.newConcurrentSet();
  /**
   * Holds not-null value if loading was disabled in current thread.
   * Initial value is {@code null} meaning loading is enabled by default.
   */
  private static final ThreadLocal<Supplier<String>> myDisabledInfo = new ThreadLocal<>();
  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Set<VirtualFile>> myForcedEnabledFiles = ThreadLocal.withInitial(() -> new THashSet<>());

  private AstLoadingFilter() {}

  public static void assertTreeLoadingEnabled(@NotNull VirtualFile file) {
    if (!Registry.is("ast.loading.filter")) return;
    if (file instanceof VirtualFileWindow) return;
    Supplier<String> disabledInfo = myDisabledInfo.get();
    if (disabledInfo == null) {
      // loading was not disabled in current thread
    }
    else if (myForcedEnabledFiles.get().contains(file)) {
      // loading was disabled but then re-enabled for file
    }
    else {
      AstLoadingException throwable = new AstLoadingException();
      if (ourReportedTraces.add(ExceptionUtil.getThrowableText(throwable))) {
        LOG.error("Tree access disabled", throwable, new Attachment("debugInfo", buildDebugInfo(file, disabledInfo)));
      }
    }
  }

  @NotNull
  private static String buildDebugInfo(@NotNull VirtualFile file, @NotNull Supplier<String> disabledInfo) {
    StringBuilder debugInfo = new StringBuilder();
    debugInfo.append("Accessed file path: ").append(file.getPath());
    String additionalInfo = disabledInfo.get();
    if (additionalInfo != null) {
      debugInfo.append('\n').append("Additional info: \n").append(additionalInfo);
    }
    return debugInfo.toString();
  }

  public static <E extends Throwable>
  void disableTreeLoading(@NotNull ThrowableRunnable<E> runnable) throws E {
    disableTreeLoading(toComputable(runnable));
  }

  public static <T, E extends Throwable>
  T disableTreeLoading(@NotNull ThrowableComputable<? extends T, E> computable) throws E {
    return disableTreeLoading(computable, () -> null);
  }

  public static <T, E extends Throwable>
  T disableTreeLoading(@NotNull ThrowableComputable<? extends T, E> computable, @NotNull Supplier<String> debugInfo) throws E {
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
  void forceEnableTreeLoading(@NotNull PsiFile psiFile, @NotNull ThrowableRunnable<E> runnable) throws E {
    forceEnableTreeLoading(psiFile, toComputable(runnable));
  }

  public static <E extends Throwable>
  void forceEnableTreeLoading(@NotNull VirtualFile virtualFile, @NotNull ThrowableRunnable<E> runnable) throws E {
    forceEnableTreeLoading(virtualFile, toComputable(runnable));
  }

  public static <T, E extends Throwable>
  T forceEnableTreeLoading(@NotNull PsiFile psiFile, @NotNull ThrowableComputable<? extends T, E> computable) throws E {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    return virtualFile == null ? computable.compute() : forceEnableTreeLoading(virtualFile, computable);
  }

  public static <T, E extends Throwable>
  T forceEnableTreeLoading(@NotNull VirtualFile virtualFile, @NotNull ThrowableComputable<? extends T, E> computable) throws E {
    Set<VirtualFile> enabledFiles = myForcedEnabledFiles.get();
    if (enabledFiles.add(virtualFile)) {
      try {
        return computable.compute();
      }
      finally {
        enabledFiles.remove(virtualFile);
      }
    }
    else {
      return computable.compute();
    }
  }

  private static <E extends Throwable> ThrowableComputable<?, E> toComputable(ThrowableRunnable<E> runnable) {
    return () -> {
      runnable.run();
      return null;
    };
  }
}
