// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;


public class JavaOptionsApplicabilityFilter extends OptionsApplicabilityFilter {
  @Override
  public boolean isOptionApplicable(final OptionId optionId) {
    // all options are applicable for Java
    return true;
  }
}
