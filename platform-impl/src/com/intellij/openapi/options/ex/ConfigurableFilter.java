package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;

public interface ConfigurableFilter {

  boolean isIncluded(Configurable configurable);

}