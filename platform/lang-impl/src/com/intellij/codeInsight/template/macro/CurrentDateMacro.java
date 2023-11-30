// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Clock;
import com.intellij.util.text.DateFormatUtil;

import java.text.SimpleDateFormat;
import java.util.Date;

final class CurrentDateMacro extends SimpleMacro {
  private static final Logger LOG = Logger.getInstance(CurrentDateMacro.class);
  private CurrentDateMacro() {
    super("date");
  }

  @Override
  protected String evaluateSimpleMacro(Expression[] params, final ExpressionContext context) {
    return formatUserDefined(params, context, true);
  }

  static String formatUserDefined(Expression[] params, ExpressionContext context, boolean date) {
    long time = Clock.getTime();
    if (params.length == 1) {
      Result format = params[0].calculateResult(context);
      if (format != null) {
        String pattern = format.toString();
        try {
          return new SimpleDateFormat(pattern).format(new Date(time));
        }
        catch (Exception e) {
          return "Problem when formatting date/time for pattern \"" + pattern + "\": " + e.getMessage();
        }
      }
    }
    return date ? DateFormatUtil.formatDate(time) : DateFormatUtil.formatTime(time);
  }
}