// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jvm.analysis;

import com.intellij.openapi.application.ex.PathManagerEx;

public final class JvmAnalysisTestsUtil {
  public static final String TEST_DATA_PROJECT_RELATIVE_BASE_PATH =
    PathManagerEx.getCommunityHomePath() + "/jvm/jvm-analysis-java-tests/testData";
}
