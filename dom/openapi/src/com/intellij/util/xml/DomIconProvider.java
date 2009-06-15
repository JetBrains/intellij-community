/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.pom.PomIconProvider;
import com.intellij.pom.PomTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class DomIconProvider extends PomIconProvider {
  public Icon getIcon(@NotNull PomTarget target, int flags) {
    if (target instanceof DomTarget) {
      return getIcon(((DomTarget)target).getDomElement(), flags);
    }
    return null;
  }

  @Nullable
  public abstract Icon getIcon(@NotNull DomElement element, int flags);
}