// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.psi;

import org.jetbrains.annotations.Nullable;


public interface RegExpNamedGroupRef extends RegExpAtom {
  @Nullable
  RegExpGroup resolve();
  @Nullable
  String getGroupName();

  boolean isPythonNamedGroupRef();
  boolean isRubyNamedGroupRef();
  boolean isNamedGroupRef();
}
