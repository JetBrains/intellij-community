/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
