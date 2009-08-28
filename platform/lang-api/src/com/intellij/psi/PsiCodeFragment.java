/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;

public interface PsiCodeFragment extends PsiFile {
  /**
   * Force search scope for this fragment
   * @param scope Scope to use when resolving references in this context
   */
  void forceResolveScope(GlobalSearchScope scope);

  GlobalSearchScope getForcedResolveScope();
}