package com.intellij.application.options;

import java.util.Map;


public interface OptionsContainingConfigurable {
  Map<String, String> processListOptions();
}
