// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

/**
 * Helper interface for PSI elements which may be injected as ForeignLeaf and need to redirect annotations
 * to another text range.
 */
public interface ExternallyAnnotated {
  /**
   * If inspection started for files with injections founds any problem in them
   * it should be able to display them locally. This method allows to define such substitution text range.
   * E.g. it may be an identifier element from C/C++ macro injection.<br/>
   * See {@code ProblemDescriptorBase} constructor for details.
   *
   * @return TextRange to which problem descriptions should be redirected, {@code null} if annotator need to skip the problem
   */
  @Nullable
  TextRange getAnnotationRegion();
}
