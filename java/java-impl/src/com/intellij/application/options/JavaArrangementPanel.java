/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.arrangement.ArrangementSettingsPanel;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/8/12 8:57 AM
 */
public class JavaArrangementPanel extends ArrangementSettingsPanel {

  public JavaArrangementPanel(@NotNull CodeStyleSettings settings) {
    super(settings, JavaLanguage.INSTANCE);
  }

  @Override
  protected int getRightMargin() {
    return 80;
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  protected String getPreviewText() {
    return null;
  }
}
