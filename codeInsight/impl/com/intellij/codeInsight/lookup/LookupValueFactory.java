/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.Iconable;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class LookupValueFactory {
  
  private LookupValueFactory() {
  }

  @NotNull
  public static Object createLookupValue(@NotNull String name, @Nullable Icon icon) {
    return icon == null ? name : new LookupValueWithIcon(name, icon);
  }

  public static class LookupValueWithIcon implements PresentableLookupValue, Iconable {
    private final String myName;
    private final Icon myIcon;

    protected LookupValueWithIcon(@NotNull String name, @NotNull Icon icon) {

      myName = name;
      myIcon = icon;
    }
    public String getPresentation() {
      return myName;
    }

    public Icon getIcon(int flags) {
      return myIcon;
    }
  }
}
