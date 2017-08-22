/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.highlighter;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JShellFileType extends LanguageFileType {
  @NonNls public static final String DEFAULT_EXTENSION = "snippet";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;
  public static final JShellFileType INSTANCE = new JShellFileType();

  private JShellFileType() {
    super(JShellLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "JSHELL";
  }

  @Override
  @NotNull
  public String getDescription() {
    return IdeBundle.message("filetype.description.jshell");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Java; // todo: a dedicated icon?
  }

  @Override
  public boolean isJVMDebuggingSupported() {
    return false;
  }
}
