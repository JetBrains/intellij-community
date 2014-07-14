/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaSectionArrangementEntry extends JavaElementArrangementEntry implements TextAwareArrangementEntry {
  @NotNull private final String myText;

  public JavaSectionArrangementEntry(@Nullable ArrangementEntry parent,
                                     @NotNull ArrangementSettingsToken type,
                                     @NotNull TextRange range,
                                     @NotNull String text,
                                     boolean canBeMatched)
  {
    super(parent, range.getStartOffset(), range.getEndOffset(), type, "SECTION", canBeMatched);
    myText = text;
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }
}
