package com.intellij.codeInsight.template.macro;

import java.text.DateFormat;
import java.util.Date;

/**
 * @author yole
 */
public class CurrentDateMacro extends SimpleMacro {
  protected CurrentDateMacro() {
    super("date");
  }

  protected String evaluate() {
    return DateFormat.getDateInstance().format(new Date());
  }
}