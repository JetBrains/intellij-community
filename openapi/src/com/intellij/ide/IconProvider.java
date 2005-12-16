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
  @Nullable
  Icon getIcon(PsiElement element, int flags);
}
