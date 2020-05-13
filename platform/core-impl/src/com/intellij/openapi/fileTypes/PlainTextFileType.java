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
package com.intellij.openapi.fileTypes;

import com.intellij.core.CoreBundle;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PlainTextFileType extends LanguageFileType implements PlainTextLikeFileType {
  public static final PlainTextFileType INSTANCE = new PlainTextFileType();

  private PlainTextFileType() {
    super(PlainTextLanguage.INSTANCE);
  }

  @Override
  @NotNull
  public String getName() {
    return "PLAIN_TEXT";
  }

  @Override
  @NotNull
  public String getDescription() {
    return CoreBundle.message("filetype.plaintext.description");
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "txt";
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Text;
  }
}
