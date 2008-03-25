/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

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

  @NotNull
  public static Object createLookupValueWithHint(@NotNull String name, @Nullable Icon icon, String hint) {
    return new LookupValueWithIconAndHint(name, icon, hint);
  }

  public static Object createLookupValueWithHintAndTail(@NotNull String name, @Nullable Icon icon, String hint, final String tail) {
    return new LookupValueWithHintAndTail(name, icon, hint, tail);
  }

  public static class LookupValueWithIcon implements PresentableLookupValue, Iconable {
    private final String myName;
    private final Icon myIcon;

    protected LookupValueWithIcon(@NotNull String name, @Nullable Icon icon) {
      myName = name;
      myIcon = icon;
    }
    public String getPresentation() {
      return myName;
    }

    public Icon getIcon(int flags) {
      return myIcon;
    }

    public boolean equals(Object a) {
      return a instanceof PresentableLookupValue && ((PresentableLookupValue)a).getPresentation().equals(myName);
    }
  }

  public static class LookupValueWithIconAndHint extends LookupValueWithIcon implements LookupValueWithUIHint {

    private final String myHint;

    protected LookupValueWithIconAndHint(final String name, final Icon icon, String hint) {
      super(name, icon);
      myHint = hint;
    }

    public String getTypeHint() {
      return myHint;
    }

    @Nullable
    public Color getColorHint() {
      return null;
    }

    public boolean isBold() {
      return false;
    }
  }

  public static class LookupValueWithHintAndTail extends LookupValueWithIconAndHint implements LookupValueWithTail {
    private final String myTail;

    protected LookupValueWithHintAndTail(final String name, final Icon icon, final String hint, String tail) {
      super(name, icon, hint);
      myTail = tail;
    }

    public String getTailText() {
      return myTail;
    }
  }

}
