package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;
import com.intellij.psi.ResolveState;

public interface NameHint {
  Key<NameHint> KEY = Key.create("NameHint");

  String getName(ResolveState state);
}
