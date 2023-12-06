// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A utility to enforce "slow operation on EDT" assertion.
 * <p/>
 * That assertion is a tool we use now to split IntelliJ Platform into "frontend" and "backend" parts
 * to avoid UI freezes caused by inherently slow operations like indexes access, slow I/O, etc.
 *
 * @see #assertSlowOperationsAreAllowed()
 */
public final class SlowOperations {
  private static final Logger LOG = Logger.getInstance(SlowOperations.class);

  private static final String ERROR_EDT = "Slow operations are prohibited on EDT. See SlowOperations.assertSlowOperationsAreAllowed javadoc.";
  private static final String ERROR_RA = "Non-cancelable slow operations are prohibited inside read action. See SlowOperations.assertNonCancelableSlowOperationsAreAllowed javadoc.";

  public static final String ACTION_UPDATE = "action.update";     // action update in menus, toolbars, and popups
  public static final String ACTION_PERFORM = "action.perform";   // user triggered actions
  public static final String KNOWN_ISSUE = "known-issues";        // known YT issue
  public static final String GENERIC = "generic";                 // generic activity

  public static final String FORCE_ASSERT = "  force assert  ";   // assertion is thrown even if disabled
  public static final String RESET = "  reset  ";                 // resets the section stack in modal dialogs

  /**
   * VM property, set to {@code true} if running in plugin development sandbox.
   */
  public static final String IDEA_PLUGIN_SANDBOX_MODE = "idea.plugin.in.sandbox.mode";

  private static int ourAlwaysAllow = -1;
  private static @NotNull FList<@NotNull String> ourStack = FList.emptyList();

  private static String ourTargetClass;
  private static final Set<String> ourReportedClasses = new HashSet<>();

  private SlowOperations() {}

  /**
   * If you get an exception from this method, then you need to move the computation to a background thread (BGT)
   * and to avoid blocking the UI thread (EDT).
   * <p/>
   * It's okay if the API changes in the process. For example, instead of wrapping an implementation of some extension
   * with deprecated {@link #allowSlowOperations} methods, it is better to admit that the EP semantic as a whole requires index access,
   * and then move the iteration over all extensions to the background on the platform-side.
   * <p/>
   * To temporarily mute the assertion in cases when it's difficult to rework the code timely,
   * the computation can be wrapped in a named section {@link #startSection}.
   * The assertion inside named sections is turned on/off separately via Registry keys {@code ide.slow.operations.assertion.<sectionName>}
   * (sections {@link #GENERIC}, {@link #ACTION_PERFORM}, ...).
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
    String error = !EDT.isCurrentThreadEdt() ||
                   isAlwaysAllowed() ||
                   isSlowOperationAllowed() ? null : ERROR_EDT;
    if (error == null || isAlreadyReported()) return;
    LOG.error(error);
  }

  /**
   * I/O and native calls in addition to being slow operations must not be called inside read-action (RA)
   * as such RAs cannot be promptly canceled on an incoming write-action (WA).
   *
   * @see #assertSlowOperationsAreAllowed()
   */
  public static void assertNonCancelableSlowOperationsAreAllowed() {
    String error = isAlwaysAllowed() ? null :
                   EDT.isCurrentThreadEdt() ? (isSlowOperationAllowed() ? null : ERROR_EDT) :
                   (ApplicationManager.getApplication().isReadAccessAllowed() ? ERROR_RA : null);
    if (error == null || isAlreadyReported()) return;
    LOG.error(error);
  }

  private static boolean isSlowOperationAllowed() {
    boolean forceAssert = isInSection(FORCE_ASSERT);
    if (!forceAssert && !Registry.is("ide.slow.operations.assertion", true)) {
      return true;
    }
    Application application = ApplicationManager.getApplication();
    if (application.isWriteAccessAllowed() && !Registry.is("ide.slow.operations.assertion.write.action")) {
      return true;
    }
    if (ourStack.isEmpty() && !Registry.is("ide.slow.operations.assertion.other", false)) {
      return true;
    }
    for (String activity : ourStack) {
      if (RESET.equals(activity)) {
        break;
      }
      if (!Registry.is("ide.slow.operations.assertion." + activity, true)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAlreadyReported() {
    if (ourTargetClass != null && !ourReportedClasses.add(ourTargetClass)) {
      return true;
    }
    Throwable throwable = new Throwable();
    return ThrowableInterner.intern(throwable) != throwable;
  }

  @ApiStatus.Internal
  public static boolean isInSection(@NotNull String sectionName) {
    EDT.assertIsEdt();
    for (String activity : ourStack) {
      if (RESET.equals(activity)) {
        break;
      }
      if (sectionName.equals(activity)) {
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

  /**
   * @deprecated To resolve EDT freezes, "slow operations" will soon be banned from EDT.
   * Consider reworking the code and the UX that needs to mute the assertion, and moving it to BGT.
   * <p/>
   * <b>DO NOT JUST CONVERT TO {@link #startSection(String)}.
   * Keep using the deprecated method if your intent is to postpone fixing the assertion for real.</b>
   */
  @Deprecated
  public static <T, E extends Throwable> T allowSlowOperations(@NotNull ThrowableComputable<T, E> computable) throws E {
    try (AccessToken ignore = startSection(GENERIC)) {
      return computable.compute();
    }
  }

  /**
   * @deprecated To resolve EDT freezes, "slow operations" will soon be banned from EDT.
   * Consider reworking the code and the UX that needs to mute the assertion, and moving it to BGT.
   * <p/>
   * <b>DO NOT JUST CONVERT TO {@link #startSection(String)}.
   * Keep using the deprecated method if your intent is to postpone fixing the assertion for real.</b>
   */
  @Deprecated
  public static <E extends Throwable> void allowSlowOperations(@NotNull ThrowableRunnable<E> runnable) throws E {
    try (AccessToken ignore = startSection(GENERIC)) {
      runnable.run();
    }
  }

  /** @noinspection unused */
  @ApiStatus.Internal
  public static @NotNull AccessToken knownIssue(@NotNull @NonNls String ytIssueId) {
    return startSection(KNOWN_ISSUE);
  }

  @ApiStatus.Internal
  public static @NotNull AccessToken reportOnceIfViolatedFor(@NotNull Object target) {
    if (!EDT.isCurrentThreadEdt()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }
    String prev = ourTargetClass;
    ourTargetClass = target.getClass().getName();
    return new AccessToken() {
      @Override
      public void finish() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourTargetClass = prev;
      }
    };
  }

  /**
   * @deprecated To resolve EDT freezes, "slow operations" will soon be banned from EDT.
   * Consider reworking the code and the UX that needs to mute the assertion, and moving it to BGT.
   * <p/>
   * <b>DO NOT JUST CONVERT TO {@link #startSection(String)}.
   * Keep using the deprecated method if your intent is to postpone fixing the assertion for real.</b>
   *
   * @param activityName see {@link #startSection(String)} javadoc
   */
  @Deprecated
  public static @NotNull AccessToken allowSlowOperations(@NotNull @NonNls String activityName) {
    return startSection(activityName);
  }

  /**
   * Starts a named logical section. Logical sections are a tool to tackle the frontend/backend splitting part-by-part,
   * and not to be overwhelmed by all that needs to be reworked all at once. Some sections have additional, hard-coded
   * semantics, like {@link #FORCE_ASSERT}, and {@link #RESET}.
   * <p/>
   * <b>This method is not for muting the assertion in places. It is intended for the common platform code.
   * USE DEPRECATED {@link #allowSlowOperations} METHODS IF YOUR INTENT IS TO POSTPONE FIXING THE ASSERTION FOR REAL.</b>
   *
   * @param sectionName reuse {@link #GENERIC} and other existing section names as much as possible.
   * <p/>
   * Use a new name <b>iff</b> you need a dedicated on/off switch for the assertion inside.
   * In that case, do not forget to add the corresponding {@code ide.slow.operations.assertion.<sectionName>} Registry key.
   *
   * @see Registry
   */
  @ApiStatus.Internal
  public static @NotNull AccessToken startSection(@NotNull @NonNls String sectionName) {
    if (!EDT.isCurrentThreadEdt()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    FList<String> prev = ourStack;
    ourStack = prev.prepend(sectionName);
    return new AccessToken() {
      @Override
      public void finish() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourStack = prev;
      }
    };
  }

  @ApiStatus.Internal
  public static boolean isMyMessage(@Nullable String error) {
    return Strings.areSameInstance(ERROR_EDT, error) ||
           Strings.areSameInstance(ERROR_RA, error);
  }
}
