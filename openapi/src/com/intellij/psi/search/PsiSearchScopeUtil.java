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
  public interface AccessScopeHandler {
    SearchScope getAccessScope(PsiElement element);
  }

  private static HashMap<FileType,AccessScopeHandler> scopeHandlers = new HashMap<FileType, AccessScopeHandler>();

  public static void registerAccessScopeHandler(FileType fileType, AccessScopeHandler accessScopeHandler) {
    scopeHandlers.put(fileType, accessScopeHandler);
  }

  public static SearchScope getAccessScope(PsiElement element) {
    if (element instanceof PsiPackage) {
      return element.getUseScope();
    }
    else if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        return new LocalSearchScope(element);
      }
      PsiFile file = element.getContainingFile();
      if (file instanceof JspFile) {
        return new LocalSearchScope(file);
      }
      PsiClass aClass = (PsiClass)element;
      if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return element.getUseScope();
      }
      else if (aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return element.getUseScope();
      }
      else if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass topClass = getTopLevelClass(aClass);
        return topClass != null ? new LocalSearchScope(topClass) : new LocalSearchScope(aClass.getContainingFile());
      }
      else {
        PsiPackage aPackage = null;
        if (file instanceof PsiJavaFile) {
          aPackage = element.getManager().findPackage(((PsiJavaFile)file).getPackageName());
        }

        if (aPackage == null) {
          PsiDirectory dir = file.getContainingDirectory();
          if (dir != null) {
            aPackage = dir.getPackage();
          }
        }

        if (aPackage != null) {
          GlobalSearchScope scope = GlobalSearchScope.packageScope(aPackage, false);
          scope = scope.intersectWith(element.getUseScope());
          return scope;
        }
        
        return new LocalSearchScope(file);
      }
    }
    else if (element instanceof PsiMethod) {
      PsiFile file = element.getContainingFile();
      if (file instanceof JspFile) {
        return new LocalSearchScope(file);
      }

      PsiMethod method = (PsiMethod)element;

      PsiClass aClass = method.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        //method from anonymous class can be called from outside the class
        PsiElement methodCallExpr = PsiTreeUtil.getParentOfType(aClass, PsiMethodCallExpression.class);
        return new LocalSearchScope(methodCallExpr != null ? methodCallExpr : aClass);
      }

      if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return element.getUseScope();
      }
      else if (method.hasModifierProperty(PsiModifier.PROTECTED)) {
        return element.getUseScope();
      }
      else if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass topClass = getTopLevelClass(method);
        return topClass != null ? new LocalSearchScope(topClass) : new LocalSearchScope(method.getContainingFile());
      }
      else {
        PsiPackage aPackage = file.getContainingDirectory().getPackage();
        if (aPackage != null) {
          GlobalSearchScope scope = GlobalSearchScope.packageScope(aPackage, false);
          scope = scope.intersectWith(element.getUseScope());
          return scope;
        }
        else {
          return new LocalSearchScope(file);
        }
      }
    }
    else if (element instanceof PsiField) {
      PsiFile file = element.getContainingFile();
      if (file instanceof JspFile) {
        return new LocalSearchScope(file);
      }
      PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.PUBLIC)) {
        return element.getUseScope();
      }
      else if (field.hasModifierProperty(PsiModifier.PROTECTED)) {
        return element.getUseScope();
      }
      else if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass topClass = getTopLevelClass(field);
        return topClass != null ? new LocalSearchScope(topClass) : new LocalSearchScope(field.getContainingFile());
      }
      else {
        final PsiDirectory directory = file.getContainingDirectory();
        PsiPackage aPackage = directory == null ? null : directory.getPackage();
        if (aPackage != null) {
          GlobalSearchScope scope = GlobalSearchScope.packageScope(aPackage, false);
          scope = scope.intersectWith(element.getUseScope());
          return scope;
        }
        else {
          return new LocalSearchScope(file);
        }
      }
    }
    else if (element instanceof ImplicitVariable) {
      return new LocalSearchScope(((ImplicitVariable)element).getDeclarationScope());
    }
    else if (element instanceof PsiLocalVariable) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiDeclarationStatement) {
        return new LocalSearchScope(parent.getParent());
      }
      else {
        return element.getUseScope();
      }
    }
    else if (element instanceof PsiParameter) {
      return new LocalSearchScope(((PsiParameter)element).getDeclarationScope());
    }
    else if (element instanceof PsiAntElement) {
      return ((PsiAntElement)element).getSearchScope();
    }
    else {
      AccessScopeHandler accessScopeHandler = scopeHandlers.get(element.getContainingFile().getFileType());
      if (accessScopeHandler!=null) return accessScopeHandler.getAccessScope(element);

      return new LocalSearchScope(element.getContainingFile());
    }
  }

  private static PsiClass getTopLevelClass(PsiElement element) {
    while (!(element.getParent() instanceof PsiFile)) {
      element = element.getParent();
    }
    return element instanceof PsiClass ? (PsiClass)element : null;
  }

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