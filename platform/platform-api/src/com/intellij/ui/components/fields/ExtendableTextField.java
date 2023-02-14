// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.fields;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.TextUI;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

public class ExtendableTextField extends JBTextField implements ExtendableTextComponent {
  private List<Extension> extensions = emptyList();

  public ExtendableTextField() {
    this(null);
  }

  public ExtendableTextField(int columns) {
    this(null, columns);
  }

  public ExtendableTextField(@Nls String text) {
    this(text, 20);
  }

  public ExtendableTextField(@Nls String text, int columns) {
    super(text, columns);
  }

  @Override
  public List<Extension> getExtensions() {
    return extensions;
  }

  @Override
  public void setExtensions(Extension... extensions) {
    setExtensions(asList(extensions));
  }

  @Override
  public void setExtensions(Collection<? extends Extension> extensions) {
    setExtensions(new ArrayList<>(extensions));
  }

  private void setExtensions(List<? extends Extension> extensions) {
    putClientProperty("JTextField.variant", null);
    this.extensions = unmodifiableList(extensions);
    putClientProperty("JTextField.variant", ExtendableTextComponent.VARIANT);
  }

  @Override
  public void addExtension(@NotNull Extension extension) {
    if (!getExtensions().contains(extension)) {
      List<Extension> extensions = new ArrayList<>(getExtensions());
      extensions.add(extension);
      setExtensions(extensions);
    }
  }

  @Override
  public void removeExtension(@NotNull Extension extension) {
    ArrayList<Extension> extensions = new ArrayList<>(getExtensions());
    if (extensions.remove(extension)) setExtensions(extensions);
  }

  /**
   * @deprecated Temporary solution to support icons in the text component for different L&F.
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

  @ApiStatus.Experimental
  public ExtendableTextField addBrowseExtension(@NotNull Runnable action, @Nullable Disposable parentDisposable) {
    KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
    String tooltip = UIBundle.message("component.with.browse.button.browse.button.tooltip.text") + " (" + KeymapUtil.getKeystrokeText(keyStroke) + ")";

    ExtendableTextComponent.Extension browseExtension =
      ExtendableTextComponent.Extension.create(AllIcons.General.OpenDisk, AllIcons.General.OpenDiskHover, tooltip, action);

    new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        action.run();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(keyStroke), this, parentDisposable);
    addExtension(browseExtension);

    return this;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleContextDelegateWithContextMenu(super.getAccessibleContext()) {
        @Override
        protected void doShowContextMenu() {
          ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true);

        }

        @Override
        protected Container getDelegateParent() {
          return getParent();
        }
      };
    }
    return accessibleContext;
  }
}
