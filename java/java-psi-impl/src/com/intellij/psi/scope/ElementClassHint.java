// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public interface ElementClassHint {
  Key<ElementClassHint> KEY = Key.create("ElementClassHint");

  enum DeclarationKind {
    CLASS,
    PACKAGE,
    METHOD,
    VARIABLE,
    FIELD,
    ENUM_CONST
  }

  boolean shouldProcess(@NotNull DeclarationKind kind);
}
