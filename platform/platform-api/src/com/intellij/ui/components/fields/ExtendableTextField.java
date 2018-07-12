// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields;

import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.TextUI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

/**
 * @author Sergey Malenkov
 */
public class ExtendableTextField extends JBTextField implements ExtendableTextComponent {
  private List<Extension> extensions = emptyList();

  public ExtendableTextField() {
    this(null);
  }

  public ExtendableTextField(int columns) {
    this(null, columns);
  }

  public ExtendableTextField(String text) {
    this(text, 20);
  }

  public ExtendableTextField(String text, int columns) {
    super(text, columns);
  }

  public List<Extension> getExtensions() {
    return extensions;
  }

  public void setExtensions(Extension... extensions) {
    setExtensions(asList(extensions));
  }

  public void setExtensions(Collection<Extension> extensions) {
    setExtensions(new ArrayList<>(extensions));
  }

  private void setExtensions(List<Extension> extensions) {
    putClientProperty("JTextField.variant", null);
    this.extensions = unmodifiableList(extensions);
    putClientProperty("JTextField.variant", ExtendableTextComponent.VARIANT);
  }

  public void addExtension(@NotNull Extension extension) {
    ArrayList<Extension> extensions = new ArrayList<>(getExtensions());
    if (extensions.add(extension)) setExtensions(extensions);
  }

  public void removeExtension(@NotNull Extension extension) {
    ArrayList<Extension> extensions = new ArrayList<>(getExtensions());
    if (extensions.remove(extension)) setExtensions(extensions);
  }

  /**
   * Temporary solution to support icons in the text component for different L&F.
   * This method replaces non-supported UI with Darcula UI.
   *
   * @param ui an object to paint this text component
   */
  @Override
  @Deprecated
  public void setUI(TextUI ui) {
    TextUI suggested = ui;
    try {
      if (ui == null || !Class.forName("com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI").isAssignableFrom(ui.getClass())) {
        ui = (TextUI)Class
          .forName("com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI")
          .getDeclaredMethod("createUI", JComponent.class)
          .invoke(null, this);
      }
    } catch (Exception ignore) {}

    super.setUI(ui);
    if (ui != suggested) {
      try {
        setBorder((Border)Class
          .forName("com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder")
          .newInstance());
      }
      catch (Exception ignore) {
      }
    }
  }
}
