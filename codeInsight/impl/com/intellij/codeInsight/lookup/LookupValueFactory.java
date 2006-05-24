/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.Iconable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class LookupValueFactory {
  
  private LookupValueFactory() {
  }

  public static Object createLookupValue(String name, Icon icon) {
    return new LookupValueWithIcon(name, icon);
  }

  public static class LookupValueWithIcon implements PresentableLookupValue, Iconable {
    private final String myName;
    private final Icon myIcon;

    protected LookupValueWithIcon(String name, Icon icon) {

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
