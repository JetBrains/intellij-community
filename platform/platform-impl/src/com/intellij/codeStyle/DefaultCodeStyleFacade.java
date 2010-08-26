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

/*
 * @author max
 */
package com.intellij.codeStyle;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultCodeStyleFacade extends CodeStyleFacade {
  public int getIndentSize(final FileType fileType) {
    return 4;
  }

  @Nullable
  public String getLineIndent(@NotNull final Editor editor) {
    return null;
  }

  public String getLineSeparator() {
    return "\n";
  }

  public int getRightMargin() {
    return 80;
  }

  @Override
  public boolean isWrapWhenTypingReachesRightMargin() {
    return false;
  }

  public int getTabSize(final FileType fileType) {
    return 4;
  }

  public boolean isSmartTabs(final FileType fileType) {
    return false;
  }

  public boolean projectUsesOwnSettings() {
    return false;
  }

  public boolean isUnsuitableCodeStyleConfigurable(final Configurable c) {
    return false;
  }

  public boolean useTabCharacter(final FileType fileType) {
    return false;
  }
}