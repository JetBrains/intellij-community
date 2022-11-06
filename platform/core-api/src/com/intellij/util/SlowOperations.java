// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class SlowOperations {
  private static final Logger LOG = Logger.getInstance(SlowOperations.class);

  public static final String ACTION_UPDATE = "action.update";
  public static final String ACTION_PERFORM = "action.perform";
  public static final String RENDERING = "rendering";
  public static final String GENERIC = "generic";
  public static final String FORCE_ASSERT = "  force assert  ";
  public static final String FAST_TRACK = "  fast track  ";
  public static final String RESET = "  reset  ";

  /**
   * VM property, set to {@code true} if running in plugin development sandbox.
   */
  public static final String IDEA_PLUGIN_SANDBOX_MODE = "idea.plugin.in.sandbox.mode";

  private static int ourAlwaysAllow = -1;
  private static @NotNull FList<@NotNull String> ourStack = FList.emptyList();

  private SlowOperations() {}

  /**
   * If you get an exception from this method, then you need to move the computation to the background
   * while also trying to avoid blocking the UI thread as well. It's okay if the API changes in the process,
   * e.g. instead of wrapping implementation of some extension into {@link #allowSlowOperations},
   * it's better to admit that the EP semantic as a whole requires index access,
   * and then move the iteration over all extensions to the background on the platform-side.
   * <p/>
   * In cases when it's impossible to do so, the computation can be wrapped in a {@link #allowSlowOperations} section explicitly.
   * Sections are named and can be enabled/disabled via Registry keys, e.g. {@link #ACTION_UPDATE}, {@link #RENDERING}, etc.
   * These sections are a temporary solution, they are tracked and solved in future.
   * <p/>
   * Action Subsystem<br><br>
   * <l>
   *   <li>
   *     If the slow part is in {@link com.intellij.openapi.actionSystem.DataProvider#getData(String)} call
   *     the provider shall be split in two parts - the fast UI part invoked on EDT and the slow part invoked in background -
   *     using {@link com.intellij.openapi.actionSystem.PlatformDataKeys#BGT_DATA_PROVIDER} data key.
   *     Slow data providers are run along with other {@code GetDataRules} in background when actions are updated.
   *   </li>
   *   <li>
   *     {@code AnAction#update}, {@code ActionGroup#getChildren}, and {@code ActionGroup#canBePerformed} should be either fast
   *     or moved to background thread by returning {@link com.intellij.openapi.actionSystem.ActionUpdateThread#BGT} in
   *     {@code AnAction#getActionUpdateThread}.
   *   </li>
   *   <li>
   *     {@code AnAction#actionPerformed} shall be explicitly coded not to block the UI thread.
   *   </li>
   * </l>
   * <p/>
   * The described logic is implemented by {@link com.intellij.openapi.actionSystem.impl.ActionUpdater}.
   * <p/>
   *
   * @see com.intellij.openapi.application.NonBlockingReadAction
   * @see com.intellij.openapi.application.CoroutinesKt#readAction
   * @see com.intellij.openapi.actionSystem.ex.ActionUtil#underModalProgress
   */
  public static void assertSlowOperationsAreAllowed() {
    if (!EDT.isCurrentThreadEdt()) {
      return;
    }
    if (isInsideActivity(FAST_TRACK)) {
      if (Cancellation.isInNonCancelableSection()) {
        reportNonCancellableSectionInFastTrack();
      }
      else {
        throw new ProcessCanceledException();
      }
    }
    if (isAlwaysAllowed()) {
      return;
    }
    boolean forceAssert = isInsideActivity(FORCE_ASSERT);
    if (!forceAssert && !Registry.is("ide.slow.operations.assertion", true)) {
      return;
    }
    Application application = ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed() && !Registry.is("ide.slow.operations.assertion.write.action")) {
      return;
    }
    if (ourStack.isEmpty() && !Registry.is("ide.slow.operations.assertion.other", false)) {
      return;
    }
    for (String activity : ourStack) {
      if (RESET.equals(activity)) {
        break;
      }
      if (!Registry.is("ide.slow.operations.assertion." + activity, true)) {
        return;
      }
    }

    Throwable throwable = new Throwable();
    if (ThrowableInterner.intern(throwable) != throwable) {
      return;
    }
    LOG.error("Slow operations are prohibited on EDT. See SlowOperations.assertSlowOperationsAreAllowed javadoc.");
  }

  private static void reportNonCancellableSectionInFastTrack() {
    LOG.error("Non-cancellable section in FAST_TRACK");
  }

  @ApiStatus.Internal
  public static boolean isInsideActivity(@NotNull String activityName) {
    EDT.assertIsEdt();
    for (String activity : ourStack) {
      if (RESET.equals(activity)) {
        break;
      }
      if (activityName.equals(activity)) {
        return true;
      }
    }
    return false;
  }

  @ApiStatus.Internal
  public static boolean isAlwaysAllowed() {
    if (ourAlwaysAllow == 1) {
      return true;
    }
    if (ourAlwaysAllow == 0) {
      return false;
    }
    if (!LoadingState.APP_STARTED.isOccurred()) {
      return true;
    }

    Application application = ApplicationManager.getApplication();
    boolean result = System.getenv("TEAMCITY_VERSION") != null ||
                     application.isUnitTestMode() ||
                     application.isCommandLine() ||
                     !application.isEAP() && !application.isInternal() && !SystemProperties
                       .getBooleanProperty(IDEA_PLUGIN_SANDBOX_MODE, false);
    ourAlwaysAllow = result ? 1 : 0;
    return result;
  }

  public static <T, E extends Throwable> T allowSlowOperations(@NotNull ThrowableComputable<T, E> computable) throws E {
    try (AccessToken ignore = allowSlowOperations(GENERIC)) {
      return computable.compute();
    }
  }

  public static <E extends Throwable> void allowSlowOperations(@NotNull ThrowableRunnable<E> runnable) throws E {
    try (AccessToken ignore = allowSlowOperations(GENERIC)) {
      runnable.run();
    }
  }

  public static @NotNull AccessToken allowSlowOperations(@NotNull @NonNls String activityName) {
    if (!EDT.isCurrentThreadEdt()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    FList<String> prev = ourStack;
    ourStack = prev.prepend(activityName);
    return new AccessToken() {
      @Override
      public void finish() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourStack = prev;
      }
    };
  }
}
