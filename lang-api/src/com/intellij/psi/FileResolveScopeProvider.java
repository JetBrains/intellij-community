package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;

/**
 * Implemented by PSI files which must have non-standard resolve scope for elements contained in them.
 *
 * @author yole
 */
public interface FileResolveScopeProvider {
  GlobalSearchScope getFileResolveScope();
}
