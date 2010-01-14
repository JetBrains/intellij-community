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
package com.intellij.ide.highlighter;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JavaFileType extends LanguageFileType {
  @NonNls public static final String DEFAULT_EXTENSION = "java";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = ".java";
  private static final Icon ICON = IconLoader.getIcon("/fileTypes/java.png");
  public static final JavaFileType INSTANCE = new JavaFileType();

  private JavaFileType() {
    super(new JavaLanguage());
  }

  @NotNull
  public String getName() {
    return "JAVA";
  }

  @NotNull
  public String getDescription() {
    return IdeBundle.message("filetype.description.java");
  }

  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  public Icon getIcon() {
    return ICON;
  }

  public boolean isJVMDebuggingSupported() {
    return true;
  }
}