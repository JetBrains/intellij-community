// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

public interface ParameterInfoListener {
  ExtensionPointName<ParameterInfoListener> EP_NAME = new ExtensionPointName<>("com.intellij.codeInsight.parameterInfo.listener");

  void showHint(ParameterInfoController.Model result);
  void hideHint(Project project);
}
