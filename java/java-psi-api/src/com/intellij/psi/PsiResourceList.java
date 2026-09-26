// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

/**
 * Represents a resource list of try-with-resources statement (automatic resource management) introduced in JDK 7.
 *
 * @see PsiTryStatement#getResourceList()
 */
public interface PsiResourceList extends PsiElement, Iterable<PsiResourceListElement> {
  /**
   * @return number of PsiResourceListElement children 
   * (unlike method name suggests, not only resource variables, but also resource expressions)
   */
  int getResourceVariablesCount();
}