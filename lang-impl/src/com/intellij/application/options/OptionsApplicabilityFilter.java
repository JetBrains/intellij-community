package com.intellij.application.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;

/**
 * @author yole
 */
public abstract class OptionsApplicabilityFilter {
  public static final ExtensionPointName<OptionsApplicabilityFilter> EP_NAME = ExtensionPointName.create("com.intellij.optionsApplicabilityFilter");

  public abstract boolean isOptionApplicable(OptionId optionId);

  public static boolean isApplicable(OptionId id) {
    for(OptionsApplicabilityFilter filter: Extensions.getExtensions(EP_NAME)) {
      if (filter.isOptionApplicable(id)) {
        return true;
      }
    }
    return false;
  }
}
