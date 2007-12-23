package com.intellij.codeInsight.template.macro;

import java.text.DateFormat;
import java.util.Date;

/**
 * @author yole
 */
public class CurrentTimeMacro extends SimpleMacro {
  protected CurrentTimeMacro() {
    super("time");
  }

  protected String evaluate() {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
  }
}