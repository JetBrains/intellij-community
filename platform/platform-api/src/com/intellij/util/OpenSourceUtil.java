// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.pom.Navigatable;
import com.intellij.pom.StatePreservingNavigatable;
import org.jetbrains.annotations.Nullable;

import static java.util.Arrays.asList;

public class OpenSourceUtil {

  private OpenSourceUtil() {
  }

  public static void openSourcesFrom(DataContext context, boolean requestFocus) {
    navigate(requestFocus, CommonDataKeys.NAVIGATABLE_ARRAY.getData(context));
  }

  public static void openSourcesFrom(DataProvider context, boolean requestFocus) {
    navigate(requestFocus, CommonDataKeys.NAVIGATABLE_ARRAY.getData(context));
  }

  /**
   * @return {@code true} if the specified {@code object} is {@link Navigatable} and supports navigation
   */
  public static boolean canNavigate(@Nullable Object object) {
    return object instanceof Navigatable && ((Navigatable)object).canNavigate();
  }

  /**
   * @return {@code true} if the specified {@code object} is {@link Navigatable} and supports navigation to source
   */
  public static boolean canNavigateToSource(@Nullable Object object) {
    return object instanceof Navigatable && ((Navigatable)object).canNavigateToSource();
  }

  /**
   * Invokes {@link #navigate(boolean, Navigatable...)} that always requests focus.
   */
  public static void navigate(@Nullable Navigatable... navigatables) {
    navigate(true, navigatables);
  }

  /**
   * Invokes {@link #navigate(boolean, boolean, Navigatable...)} that does not try to preserve a state of a corresponding editor.
   */
  public static void navigate(boolean requestFocus, @Nullable Navigatable... navigatables) {
    navigate(requestFocus, false, navigatables);
  }

  /**
   * Invokes {@link #navigate(boolean, boolean, Iterable)} if at least one navigatable exists
   */
  public static void navigate(boolean requestFocus, boolean tryNotToScroll, @Nullable Navigatable... navigatables) {
    if (navigatables != null && navigatables.length > 0) navigate(requestFocus, tryNotToScroll, asList(navigatables));
  }

  /**
   * Navigates to all available sources or to the first navigatable that represents non-source navigation.
   *
   * @param requestFocus   specifies whether a focus should be requested or not
   * @param tryNotToScroll specifies whether a corresponding editor should preserve its state if it is possible
   * @param navigatables   an iterable collection of navigatables
   * @return {@code true} if at least one navigatable was processed, {@code false} otherwise
   */
  public static boolean navigate(boolean requestFocus, boolean tryNotToScroll, @Nullable Iterable<Navigatable> navigatables) {
    if (navigatables == null) return false;
    Navigatable nonSourceNavigatable = null;
    boolean alreadyNavigatedToSource = false;
    for (Navigatable navigatable : navigatables) {
      if (navigateToSource(requestFocus, tryNotToScroll, navigatable)) {
        alreadyNavigatedToSource = true;
      }
      else if (!alreadyNavigatedToSource && nonSourceNavigatable == null && canNavigate(navigatable)) {
        nonSourceNavigatable = navigatable;
      }
    }
    if (alreadyNavigatedToSource) return true;
    if (nonSourceNavigatable == null) return false;
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
   */
  public static boolean navigateToSource(boolean requestFocus, boolean tryNotToScroll, @Nullable Iterable<Navigatable> navigatables) {
    if (navigatables == null) return false;
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
    if (!canNavigateToSource(navigatable)) return false;
    if (tryNotToScroll && navigatable instanceof StatePreservingNavigatable) {
      ((StatePreservingNavigatable)navigatable).navigate(requestFocus, true);
    }
    else {
      navigatable.navigate(requestFocus);
    }
    return true;
  }
}
