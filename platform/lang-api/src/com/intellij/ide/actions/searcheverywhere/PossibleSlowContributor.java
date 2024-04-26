// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

/**
 * This marker interface represents contributors that may process items slowly. The search process can start
 * displaying initial results without waiting for input from such contributors.
 *
 * Currently, this interface is not in use. By default, all contributors are
 * assumed slow unless they are explicitly marked as an {@link EssentialContributor}.
 * Nevertheless, this interface may be repurposed and applied for enhanced checks in the future.
 */
public interface PossibleSlowContributor {

  default boolean isSlow() {
    return true;
  }

  static boolean checkSlow(SearchEverywhereContributor<?> contributor) {
    return (contributor instanceof PossibleSlowContributor) && ((PossibleSlowContributor)contributor).isSlow();
  }
}
