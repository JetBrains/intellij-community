// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Represents a separator.
 */
@SuppressWarnings("ComponentNotRegistered")
public final class Separator extends AnAction implements DumbAware, LightEditCompatible {

  private static final Separator ourInstance = new Separator();
  private final Supplier<@NlsContexts.Separator String> myDynamicText;

  @NotNull
  public static Separator getInstance() {
    return ourInstance;
  }

  @NotNull
  public static Separator create() {
    return create(null);
  }

  @NotNull
  public static Separator create(@Nullable @NlsContexts.Separator String text) {
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

  public @NlsContexts.Separator String getText() {
    return myDynamicText.get();
  }

  @Override
  public String toString() {
    return IdeBundle.message("action.separator", myDynamicText.get());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e){
    throw new UnsupportedOperationException();
  }
}
