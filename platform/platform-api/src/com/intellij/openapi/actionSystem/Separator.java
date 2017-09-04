/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a separator.
 */
public final class Separator extends AnAction implements DumbAware {

  private static final Separator ourInstance = new Separator();

  @NotNull
  public static Separator getInstance() {
    return ourInstance;
  }

  @NotNull
  public static Separator create() {
    return create(null);
  }

  @NotNull
  public static Separator create(@Nullable String text) {
    return StringUtil.isEmptyOrSpaces(text)? ourInstance : new Separator(text);
  }

  private final String myText;

  public Separator() {
    myText = null;
  }

  public Separator(@Nullable String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  @Override
  public String toString() {
    return "Separator (" + myText + ")";
  }

  @Override
  public void actionPerformed(AnActionEvent e){
    throw new UnsupportedOperationException();
  }
}
