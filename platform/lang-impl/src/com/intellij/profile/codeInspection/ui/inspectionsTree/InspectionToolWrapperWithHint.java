// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

public interface InspectionToolWrapperWithHint {
  /**
   * Hint will be displayed in gray after inspection name
   */
  @NlsContexts.Label
  @Nullable String getHint();
}
