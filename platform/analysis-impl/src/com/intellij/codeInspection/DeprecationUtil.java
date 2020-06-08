// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisBundle;

public final class DeprecationUtil {
  public static final String DEPRECATION_SHORT_NAME = "Deprecation";

  public static final String DEPRECATION_ID = "deprecation";

  public static final String FOR_REMOVAL_SHORT_NAME = "MarkedForRemoval";

  public static final String FOR_REMOVAL_ID = "removal";

  public static String getDeprecationDisplayName() {
    return AnalysisBundle.message("inspection.deprecated.display.name");
  }

  public static String getForRemovalDisplayName() {
    return AnalysisBundle.message("inspection.marked.for.removal.display.name");
  }
}