// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.psi;

import org.jetbrains.annotations.Nullable;

/**
 * A reference back to a named group (RegExpGroup).
 */
public interface RegExpNamedGroupRef extends RegExpAtom {

  /**
   * @return the referenced named group, or null if no such group exists
   */
  @Nullable RegExpGroup resolve();

  /**
   * @return the name of the group referenced.
   */
  @Nullable String getGroupName();

  boolean isPythonNamedGroupRef();
  boolean isRubyNamedGroupRef();
  boolean isNamedGroupRef();
}
