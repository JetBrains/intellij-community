// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface PostfixTemplateEditor<T extends PostfixTemplate> extends Disposable {
  T createTemplate(@NotNull String templateId);
  
  void setTemplate(T template);

  @NotNull
  JComponent getComponent();
}
