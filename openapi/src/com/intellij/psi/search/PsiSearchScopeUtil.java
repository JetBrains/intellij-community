/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.ant.PsiAntElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.HashMap;

public class PsiSearchScopeUtil {
  //TODO: move to SearchScope itself
  public static SearchScope scopesUnion(SearchScope scope1, SearchScope scope2) {
    if (scope1 instanceof LocalSearchScope) {
      LocalSearchScope _scope1 = (LocalSearchScope)scope1;
      if (scope2 instanceof LocalSearchScope) {
        LocalSearchScope _scope2 = (LocalSearchScope)scope2;
        return _scope1.union(_scope2);
      }
      else {
        PsiElement[] elements1 = _scope1.getScope();
        for (int i = 0; i < elements1.length; i++) {
          final PsiElement element = elements1[i];
          if (isInScope(scope2, element)) return scope2;
        }
        return null;
      }
    }
    else if (scope2 instanceof LocalSearchScope) {
      return scopesUnion(scope2, scope1);
    }
    else {
      final GlobalSearchScope _scope1 = (GlobalSearchScope)scope1;
      final GlobalSearchScope _scope2 = (GlobalSearchScope)scope2;
      return new GlobalSearchScope() {
        public boolean contains(VirtualFile file) {
          return _scope1.contains(file) || _scope2.contains(file);
        }

        public int compare(VirtualFile file1, VirtualFile file2) {
          return 0; //TODO?
        }

        public boolean isSearchInModuleContent(Module aModule) {
          return _scope1.isSearchInModuleContent(aModule) || _scope2.isSearchInModuleContent(aModule);
        }

        public boolean isSearchInLibraries() {
          return _scope1.isSearchInLibraries() || _scope2.isSearchInLibraries();
        }
      };
    }
  }

  public static boolean isInScope(SearchScope scope, PsiElement element) {
    if (scope instanceof LocalSearchScope) {
      PsiElement[] scopeElements = ((LocalSearchScope)scope).getScope();
      for (int i = 0; i < scopeElements.length; i++) {
        final PsiElement scopeElement = scopeElements[i];
        if (PsiTreeUtil.isAncestor(scopeElement, element, false)) return true;
      }
      return false;
    }
    else {
      GlobalSearchScope _scope = (GlobalSearchScope)scope;

      PsiFile file = element.getContainingFile();
      if (file != null) {
        if (file.getVirtualFile() == null) return true; //?
        if (!_scope.contains(file.getVirtualFile())) return false;
        return true;
      }
      else {
        return true;
      }
    }
  }
}