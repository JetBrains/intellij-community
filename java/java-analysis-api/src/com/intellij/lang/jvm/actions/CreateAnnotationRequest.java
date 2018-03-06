// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtilRt;

import java.util.List;

public interface CreateAnnotationRequest extends ActionRequest {

  default String getActionName() {
    return "Add " + StringUtilRt.getShortName(getAnnotationName()); // TODO: i11n, and mb drop this method
  }

  ;

  String getAnnotationName();

  String getValueLiteralValue();

  List<LookupElement> getValueExpression();
}
