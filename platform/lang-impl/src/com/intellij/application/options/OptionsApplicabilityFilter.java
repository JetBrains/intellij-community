// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.application.options;

import com.intellij.openapi.extensions.ExtensionPointName;


public abstract class OptionsApplicabilityFilter {
  public static final ExtensionPointName<OptionsApplicabilityFilter> EP_NAME = ExtensionPointName.create("com.intellij.optionsApplicabilityFilter");

  public abstract boolean isOptionApplicable(OptionId optionId);

  public static boolean isApplicable(OptionId id) {
    for(OptionsApplicabilityFilter filter: EP_NAME.getExtensionList()) {
      if (filter.isOptionApplicable(id)) {
        return true;
      }
    }
    return false;
  }
}
