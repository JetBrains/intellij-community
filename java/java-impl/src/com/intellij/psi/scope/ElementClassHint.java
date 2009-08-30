package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;

public interface ElementClassHint {
  Key<ElementClassHint> KEY = Key.create("ElementClassHint");

  enum DeclaractionKind {
    CLASS,
    PACKAGE,
    METHOD,
    VARIABLE,
    FIELD,
    ENUM_CONST
  }

  boolean shouldProcess(DeclaractionKind kind);
}
