// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.application.ex.PathManagerEx;

public final class PathJavaTestUtil {
  public static String getCommunityJavaTestDataPath() {
    PathManagerEx.TestDataLookupStrategy strategy = PathManagerEx.guessTestDataLookupStrategy();
    if (strategy == PathManagerEx.TestDataLookupStrategy.ULTIMATE) {
      strategy = PathManagerEx.TestDataLookupStrategy.COMMUNITY_FROM_ULTIMATE;
    }
    return PathManagerEx.getTestDataPath(strategy);
  }
}
