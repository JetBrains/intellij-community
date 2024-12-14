// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Represents a separator.
 */
public final class Separator extends DecorativeElement implements DumbAware, LightEditCompatible, SeparatorAction {

  private static final Separator ourInstance = new Separator();
  private final Supplier<@NlsContexts.Separator String> myDynamicText;

  public static @NotNull Separator getInstance() {
    return ourInstance;
  }

  public static @NotNull Separator create() {
    return create(null);
  }

  public static @NotNull Separator create(@Nullable @NlsContexts.Separator String text) {
    return StringUtil.isEmptyOrSpaces(text)? ourInstance : new Separator(text);
  }

  public Separator() {
    myDynamicText = () -> null;
  }

  public Separator(@Nullable @NlsContexts.Separator String text) {
    myDynamicText = () -> text;
  }

  public Separator(@NotNull Supplier<@NlsContexts.Separator String> dynamicText) {
    myDynamicText = dynamicText;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public @NlsContexts.Separator String getText() {
    return myDynamicText.get();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    Separator other = (Separator) obj;
    return Objects.equals(myDynamicText, other.myDynamicText);
  }

  @Override
  public String toString() {
    return IdeBundle.message("action.separator", myDynamicText.get());
  }
}
