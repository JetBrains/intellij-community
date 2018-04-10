// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * Helper interface for PSI elements which may be added as ForeignLeaf and need to redirect annotations
 * to another text range.
 */
@FunctionalInterface
public interface ExternallyAnnotated {
  /**
   * If inspection started for files with ForeignLeaf substitutions founds any problem in them
   * it should be able to display it locally. This method allows to define such substitution text range.
   * An example from C/C++ macro substitution:<br/><pre>
   * #define T(x) int x
   * int k;
   * T(k) = 0; // the problem [duplicate declaration 'k'] needs to be annotated
   *           // for macro parameter, not declarator
   * </pre><br/>
   * See {@link com.intellij.codeInspection.ProblemDescriptorBase} constructor for details.
   *
   * @return TextRange to which problem descriptions should be redirected, {@code null} if annotator needs to skip the problem
   */
  @Nullable
  TextRange getAnnotationRegion();
}
