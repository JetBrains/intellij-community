// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.TextWithIcon;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class ModuleRendererFactory {

  private static final ExtensionPointName<ModuleRendererFactory> EP_NAME = ExtensionPointName.create("com.intellij.moduleRendererFactory");

  public static @NotNull ModuleRendererFactory findInstance(Object element) {
    for (ModuleRendererFactory factory : EP_NAME.getExtensions()) {
      if (factory.handles(element)) {
        return factory;
      }
    }
    assert false : "No factory found for " + element;
    return null;
  }

  public boolean rendersLocationString() {
    return false;
  }

  protected boolean handles(final Object element) {
    return true;
  }

  /**
   * This method might be invoked in the background, which will lock {@link Component.AWTTreeLock}.
   * In fact, this method is not needed since implementation only needs to provide a text and an icon.
   *
   * @deprecated call/implement {@link #getModuleTextWithIcon} instead
   */
  @Deprecated
  public @NotNull DefaultListCellRenderer getModuleRenderer() {
    if (isGetModuleTextWithIconOverridden) {
      return new PsiElementModuleRenderer(this::getModuleTextWithIcon);
    }
    throw new AbstractMethodError("getModuleTextWithIcon(Object) must be implemented");
  }

  private final boolean isGetModuleTextWithIconOverridden = ReflectionUtil.getMethodDeclaringClass(
    getClass(), "getModuleTextWithIcon", Object.class
  ) != ModuleRendererFactory.class;

  @RequiresReadLock
  @RequiresBackgroundThread(generateAssertion = false)
  public @Nullable TextWithIcon getModuleTextWithIcon(Object element) {
    return getTextWithIcon(getModuleRenderer(), element);
  }

  static @Nullable TextWithIcon getTextWithIcon(@Nullable ListCellRenderer<Object> renderer, Object element) {
    if (renderer == null) {
      return null;
    }
    Component component = renderer.getListCellRendererComponent(new JList<>(), element, -1, false, false);
    if (!(component instanceof JLabel)) {
      return null;
    }
    JLabel label = (JLabel)component;
    return new TextWithIcon(label.getText(), label.getIcon());
  }
}
