// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

public class JBPasswordField extends JPasswordField implements ComponentWithEmptyText {
  private final TextComponentEmptyText myEmptyText;

  public JBPasswordField() {
    myEmptyText = new TextComponentEmptyText(this, false);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myEmptyText.paintStatusText(g);
  }

  @Override
  public @NotNull StatusText getEmptyText() {
    return myEmptyText;
  }

  public void setPasswordIsStored(boolean stored) {
    if (stored) {
      myEmptyText.setText(IdeBundle.message("text.password.hidden"), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    else {
      myEmptyText.clear();
    }
  }


  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJBPasswordField();
    }
    return accessibleContext;
  }

  private class AccessibleJBPasswordField extends AccessibleJPasswordField {
    @Override
    public String getAccessibleDescription() {
      String description = super.getAccessibleDescription();
      if (description == null && StringUtil.isEmpty(new String(getPassword()))) {
        //noinspection HardCodedStringLiteral
        String emptyText = myEmptyText.toString();
        if (!emptyText.isEmpty()) {
          return AccessibleContextUtil.getUniqueDescription(this, emptyText);
        }
      }
      return description;
    }
  }
}
