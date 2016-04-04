/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a package. 
 *  @author dsl
 */
public class PackageWrapper {
  private final PsiManager myManager;
  @NotNull private final String myQualifiedName;

  public PackageWrapper(PsiManager manager, @NotNull String qualifiedName) {
    myManager = manager;
    myQualifiedName = qualifiedName;
  }

  public PackageWrapper(PsiPackage aPackage) {
    myManager = aPackage.getManager();
    myQualifiedName = aPackage.getQualifiedName();
  }

  public PsiManager getManager() { return myManager; }

  public PsiDirectory[] getDirectories() {
    String qName = myQualifiedName;
    while (qName.endsWith(".")) {
      qName = StringUtil.trimEnd(qName, ".");
    }
    final PsiPackage aPackage = JavaPsiFacade.getInstance(myManager.getProject()).findPackage(qName);
    if (aPackage != null) {
      return aPackage.getDirectories();
    } else {
      return PsiDirectory.EMPTY_ARRAY;
    }
  }

  public boolean exists() {
    final Project project = myManager.getProject();
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(myQualifiedName);
    return aPackage != null && aPackage.getDirectories(GlobalSearchScope.projectScope(project)).length > 0;
  }

  @NotNull
  public String getQualifiedName() {
    return myQualifiedName;
  }

  public boolean equalToPackage(PsiPackage aPackage) {
    return aPackage != null && myQualifiedName.equals(aPackage.getQualifiedName());
  }

  public static PackageWrapper create(PsiPackage aPackage) {
    return new PackageWrapper(aPackage);
  }
}
