// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

public interface PossibleSlowContributor {

  default boolean isSlow() {
    return true;
  }

  static boolean checkSlow(SearchEverywhereContributor<?> contributor) {
    return (contributor instanceof PossibleSlowContributor) && ((PossibleSlowContributor)contributor).isSlow();
  }
}
