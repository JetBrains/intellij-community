// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.codeInsight.lookup.LookupElement;

import java.util.List;

public interface CreateAnnotationRequest extends ActionRequest {

  String getAnnotationName();

  String getValueLiteralValue();

  List<LookupElement> getValueExpression();
}
