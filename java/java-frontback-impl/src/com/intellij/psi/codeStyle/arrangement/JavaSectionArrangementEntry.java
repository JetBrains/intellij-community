// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaSectionArrangementEntry extends JavaElementArrangementEntry implements TextAwareArrangementEntry {
  private final @NotNull String myText;

  public JavaSectionArrangementEntry(@Nullable ArrangementEntry parent,
                                     @NotNull ArrangementSettingsToken type,
                                     @NotNull TextRange range,
                                     @NotNull String text,
                                     boolean canBeMatched)
  {
    super(parent, range.getStartOffset(), range.getEndOffset(), type, "SECTION", canBeMatched);
    myText = text;
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }
}
