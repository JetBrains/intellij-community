// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.psi;

/**
 * @author yole
 */
public interface RegExpConditional extends RegExpAtom {

  /**
   * Returns condition of this conditional. This can be a numeric group reference, named group reference or lookaround group.
   * @return a RegExpBackRef, RegExpNamedGroupRef or RegExpGroup instance.
   */
  RegExpAtom getCondition();
}
