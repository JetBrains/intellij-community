package com.intellij.codeInsight.template.macro;

import com.intellij.util.SystemProperties;

/**
 * @author yole
 */
public class CurrentUserMacro extends SimpleMacro {
  protected CurrentUserMacro() {
    super("user");
  }

  protected String evaluate() {
    return SystemProperties.getUserName();
  }
}