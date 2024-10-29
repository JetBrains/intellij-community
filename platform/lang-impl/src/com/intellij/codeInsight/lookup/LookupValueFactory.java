// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.lookup;

import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @deprecated use {@link LookupElementBuilder}
 * @author Dmitry Avdeev
 */
@Deprecated(forRemoval = true)
public final class LookupValueFactory {

  private LookupValueFactory() {
  }

  public static @NotNull Object createLookupValue(@NotNull String name, @Nullable Icon icon) {
    return icon == null ? name : new LookupValueWithIcon(name, icon);
  }

  public static @NotNull Object createLookupValueWithHint(@NotNull String name, @Nullable Icon icon, String hint) {
    return new LookupValueWithIconAndHint(name, icon, hint);
  }

  public static class LookupValueWithIcon implements PresentableLookupValue, Iconable {
    private final String myName;
    private final Icon myIcon;

    protected LookupValueWithIcon(@NotNull String name, @Nullable Icon icon) {
      myName = name;
      myIcon = icon;
    }
    @Override
    public String getPresentation() {
      return myName;
    }

    @Override
    public Icon getIcon(int flags) {
      return myIcon;
    }

    @Override
    public int hashCode() {
      return getPresentation().hashCode();
    }

    public boolean equals(Object a) {
      return a != null && a.getClass() == getClass() && ((PresentableLookupValue)a).getPresentation().equals(getPresentation());
    }
  }

  public static final class LookupValueWithIconAndHint extends LookupValueWithIcon implements LookupValueWithUIHint {

    private final String myHint;

    private LookupValueWithIconAndHint(final String name, final Icon icon, String hint) {
      super(name, icon);
      myHint = hint;
    }

    @Override
    public String getTypeHint() {
      return myHint;
    }
  }
}
