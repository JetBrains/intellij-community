// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface EditorTextComponent {

  @NlsSafe
  String getText();

  JComponent getComponent();

  @NotNull
  Document getDocument();

  void addDocumentListener(@NotNull DocumentListener listener);

  void removeDocumentListener(@NotNull DocumentListener listener);
}
