/*
 * Copyright 2000-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.actionSystem;

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
  private final Supplier<String> myDynamicText;

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

  public String getText() {
    return myDynamicText.get();
  }

  @Override
  public String toString() {
    return "Separator (" + myDynamicText.get() + ")";
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e){
    throw new UnsupportedOperationException();
  }
}
