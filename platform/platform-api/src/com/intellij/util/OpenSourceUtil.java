// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.Navigatable;
import com.intellij.pom.StatePreservingNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class OpenSourceUtil {
  private OpenSourceUtil() {
  }

  public static void openSourcesFrom(@NotNull DataContext context, boolean requestFocus) {
    if (Registry.is("ide.navigation.requests")) {
      OpenSourceUtilKt.openSourcesFrom(context, requestFocus);
      return;
    }
    navigate(requestFocus, false, CommonDataKeys.NAVIGATABLE_ARRAY.getData(context));
  }

  /**
   * Invokes {@link #navigate(boolean, Navigatable...)} that always requests focus.
   */
  public static void navigate(Navigatable @Nullable ... navigatables) {
    navigate(true, navigatables);
  }

  /**
   * Invokes {@link #navigate(boolean, boolean, Navigatable...)} that does not try to preserve a state of a corresponding editor.
   */
  public static void navigate(boolean requestFocus, Navigatable @Nullable ... navigatables) {
    navigate(requestFocus, false, navigatables);
  }

  /**
   * Navigates to all available sources or to the first navigatable that represents non-source navigation.
   * <p>When the {@code ide.navigation.requests} registry flag is enabled and a project can be derived from the navigatables,
   * the navigation is submitted asynchronously and this method returns before it completes.
   * Tests that need the result must await via {@code NavigationTestUtil.awaitPendingNavigation}.
   * NB: a synchronous legacy call under Write Action is deferred to a later EDT event.
   *
   * @param requestFocus   specifies whether a focus should be requested or not
   * @param tryNotToScroll specifies whether a corresponding editor should preserve its state if it is possible
   * @param navigatables   navigatables to process
   */
  public static void navigate(boolean requestFocus, boolean tryNotToScroll, Navigatable @Nullable ... navigatables) {
    if (navigatables != null && navigatables.length > 0) {
      doNavigate(requestFocus, tryNotToScroll, List.of(navigatables));
    }
  }

  /**
   * Navigates to all available sources or to the first navigatable that represents non-source navigation.
   * <p>See {@link #navigate(boolean, boolean, Navigatable...)} for the dispatch semantics.
   *
   * @param requestFocus   specifies whether a focus should be requested or not
   * @param tryNotToScroll specifies whether a corresponding editor should preserve its state if it is possible
   * @param navigatables   an iterable collection of navigatables
   *
   * @return best-effort status: when navigation is submitted asynchronously or deferred,
   * {@code true} means that a navigation request was submitted, not that it was processed.
   * @deprecated the return value is not reliable with asynchronous navigation;
   * use {@link #navigate(boolean, boolean, Navigatable...)} instead
   */
  @Deprecated
  public static boolean navigate(boolean requestFocus, boolean tryNotToScroll, @Nullable Iterable<? extends Navigatable> navigatables) {
    return doNavigate(requestFocus, tryNotToScroll, navigatables);
  }

  private static boolean doNavigate(boolean requestFocus, boolean tryNotToScroll, @Nullable Iterable<? extends Navigatable> navigatables) {
    if (navigatables == null) {
      return false;
    }
    if (Registry.is("ide.navigation.requests")) {
      Project project = OpenSourceUtilKt.findProject(navigatables);
      if (project != null) {
        OpenSourceUtilKt.navigate(project, requestFocus, tryNotToScroll, navigatables);
        return true;
      }
    }

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      ModalityState modalityState = ModalityState.defaultModalityState();
      ApplicationManager.getApplication().invokeLater(
        () -> doNavigate(requestFocus, tryNotToScroll, navigatables), modalityState);
      return true;
    }

    Navigatable nonSourceNavigatable = null;

    int maxSourcesToNavigate = Registry.intValue("ide.source.file.navigation.limit", 100);
    int navigatedSourcesCounter = 0;
    for (Navigatable navigatable : navigatables) {
      if (maxSourcesToNavigate > 0 && navigatedSourcesCounter >= maxSourcesToNavigate) {
        break;
      }

      if (navigateToSource(requestFocus, tryNotToScroll, navigatable)) {
        navigatedSourcesCounter++;
      }
      else if (navigatedSourcesCounter == 0 && nonSourceNavigatable == null && navigatable != null && navigatable.canNavigate()) {
        nonSourceNavigatable = navigatable;
      }
    }
    if (navigatedSourcesCounter > 0) {
      return true;
    }
    if (nonSourceNavigatable == null) {
      return false;
    }
    nonSourceNavigatable.navigate(requestFocus);
    return true;
  }

  /**
   * Navigates to all available sources of the specified navigatables.
   *
   * @param requestFocus   specifies whether a focus should be requested or not
   * @param tryNotToScroll specifies whether a corresponding editor should preserve its state if it is possible
   * @param navigatables   an iterable collection of navigatables
   * @return {@code true} if at least one navigatable was processed, {@code false} otherwise
   * @deprecated prefer using void-returning alternatives
   */
  @Deprecated
  public static boolean navigateToSource(boolean requestFocus,
                                         boolean tryNotToScroll,
                                         @Nullable Iterable<? extends Navigatable> navigatables) {
    if (navigatables == null) {
      return false;
    }
    boolean alreadyNavigatedToSource = false;
    for (Navigatable navigatable : navigatables) {
      if (navigateToSource(requestFocus, tryNotToScroll, navigatable)) {
        alreadyNavigatedToSource = true;
      }
    }
    return alreadyNavigatedToSource;
  }

  /**
   * Navigates to source of the specified navigatable.
   *
   * @param requestFocus   specifies whether a focus should be requested or not
   * @param tryNotToScroll specifies whether a corresponding editor should preserve its state if it is possible
   * @return {@code true} if navigation is done, {@code false} otherwise
   */
  public static boolean navigateToSource(boolean requestFocus, boolean tryNotToScroll, @Nullable Navigatable navigatable) {
    if (navigatable == null) {
      return false;
    }
    if (Registry.is("ide.navigation.requests")) {
      Project project = OpenSourceUtilKt.findProject(navigatable);
      if (project != null) {
        return OpenSourceUtilKt.navigateToSource(project, requestFocus, tryNotToScroll, navigatable);
      }
    }
    if (!navigatable.canNavigateToSource()) {
      return false;
    }
    if (tryNotToScroll && navigatable instanceof StatePreservingNavigatable) {
      ((StatePreservingNavigatable)navigatable).navigate(requestFocus, true);
    }
    else {
      navigatable.navigate(requestFocus);
    }
    return true;
  }
}
