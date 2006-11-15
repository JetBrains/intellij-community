/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.ide;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public interface IconProvider extends ApplicationComponent {
  /**
   * @param element for which icon is shown
   * @param flags used for customizing the icon appearance. Flags are listed in {@link com.intellij.openapi.util.Iconable}
   * @see com.intellij.openapi.util.Iconable
   */
  @Nullable
  Icon getIcon(PsiElement element, int flags);
}
