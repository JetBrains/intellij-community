// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
final class TemplateContextTypes {

  static final NotNullLazyValue<ExtensionPoint<TemplateContextType>> TEMPLATE_CONTEXT_EP =
    NotNullLazyValue.createValue(() -> TemplateContextType.EP_NAME.getPoint());

  @NotNull
  public static List<TemplateContextType> getAllContextTypes() {
    return TEMPLATE_CONTEXT_EP.getValue().getExtensionList();
  }
}
