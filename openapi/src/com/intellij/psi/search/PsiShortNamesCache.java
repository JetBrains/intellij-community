/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.HashSet;

public interface PsiShortNamesCache {
  void runStartupActivity();
  PsiFile[] getFilesByName(String name);
  String [] getAllFileNames();

  PsiClass[] getClassesByName(String name, GlobalSearchScope scope);
  String  [] getAllClassNames(boolean searchInLibraries);
  void       getAllClassNames(boolean searchInLibraries, HashSet<String> dest);

  PsiMethod[] getMethodsByName(String name, GlobalSearchScope scope);
  String   [] getAllMethodNames(boolean searchInLibraries);
  void        getAllMethodNames(boolean searchInLibraries, HashSet<String> set);

  PsiField[] getFieldsByName (String name, GlobalSearchScope scope);
  String  [] getAllFieldNames(boolean searchInLibraries);
  void       getAllFieldNames(boolean checkBoxState, HashSet<String> set);
}
