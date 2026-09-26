// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import java.awt.BorderLayout;

@ApiStatus.Internal
public class LabeledEditor extends JPanel {

  private final Border myEditorBorder;
  private JComponent myMainComponent;

  public LabeledEditor(@Nullable Border editorBorder) {
    super(new BorderLayout());
    myEditorBorder = editorBorder;
  }

  public LabeledEditor() {
    this(null);
  }

  public void setComponent(@NotNull JComponent component, @NotNull JComponent titleComponent) {
    myMainComponent = component;
    removeAll();

    JPanel title = new JPanel(new BorderLayout());
    title.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    title.add(titleComponent);
    revalidate();

    final JPanel p = new JPanel(new BorderLayout());
    if (myEditorBorder != null) {
      p.setBorder(myEditorBorder);
    }
    p.add(component, BorderLayout.CENTER);
    add(p, BorderLayout.CENTER);
    add(title, BorderLayout.NORTH);
  }

  public void updateTitle(JComponent title) {
    setComponent(myMainComponent, title);
  }
}
