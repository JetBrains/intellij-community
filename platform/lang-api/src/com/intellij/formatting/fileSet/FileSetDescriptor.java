// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import com.intellij.psi.PsiFile;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileSetDescriptor {
  /**
   * Checks if the file set entry matches the given PSI file.
   *
   * @param psiFile The PSI file to check against.
   * @return True if there is a match, false otherwise.
   */
  boolean matches(@NotNull PsiFile psiFile);

  @Nullable
  default String getName() {
    return null;
  }

  @NotNull
  String getType();

  @Nullable
  String getPattern();

  void setPattern(@Nullable String pattern);

  @NotNull
  default State getState() {
    return new FileSetDescriptor.State(getType(), getName(), getPattern());
  }

  @Tag("fileSet")
  final class State {
    @Attribute("type")
    public String type;

    @Attribute("name")
    @Nullable
    public String name;

    @Attribute("pattern")
    @Nullable
    public String pattern;

    public State() {
    }

    public State(String type, @Nullable String name, @Nullable String pattern) {
      this.type = type;
      this.name = name;
      this.pattern = pattern;
    }
  }
}
