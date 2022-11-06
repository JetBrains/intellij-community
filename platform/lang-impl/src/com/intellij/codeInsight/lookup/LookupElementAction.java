// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.util.NlsContexts.ListItem;

public abstract class LookupElementAction {
  private final Icon myIcon;
  private final @ListItem String myText;

  protected LookupElementAction(@Nullable Icon icon, @NotNull @ListItem String text) {
    myIcon = icon;
    myText = text;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  public @ListItem String getText() {
    return myText;
  }

  public abstract Result performLookupAction();

  public static class Result {
    public static final Result HIDE_LOOKUP = new Result();
    public static final Result REFRESH_ITEM = new Result();

    public static class ChooseItem extends Result {
      public final LookupElement item;

      public ChooseItem(LookupElement item) {
        this.item = item;
      }
    }
  }
}
