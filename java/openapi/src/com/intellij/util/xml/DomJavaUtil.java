/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomJavaUtil {
  private DomJavaUtil() {
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name,
                                   @NotNull PsiFile file,
                                   @Nullable final Module module,
                                   @Nullable final GlobalSearchScope searchScope) {
    return findClass(name, file, module, searchScope, false);
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name,
                                   @NotNull PsiFile file,
                                   @Nullable final Module module,
                                   @Nullable GlobalSearchScope searchScope, boolean searchAnonymous) {
    if (name == null) return null;
    searchScope = searchScope != null ? searchScope : calcScope(file, module);
    final PsiClass aClass;
    if (searchAnonymous) {
      aClass = ClassUtil.findPsiClass(file.getManager(), name, null, false, searchScope);
    }
    else {
      String fqn = name.indexOf('$') >= 0 ? name.replace('$', '.') : name;
      aClass = JavaPsiFacade.getInstance(file.getProject()).findClass(fqn, searchScope);
    }
    if (aClass != null) {
      assert aClass.isValid() : name;
    }
    return aClass;
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name, @NotNull DomElement element) {
    return findClass(name, element, false);
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name, @NotNull DomElement element, boolean searchAnonymous) {
    assert element.isValid();
    if (DomUtil.hasXml(element)) {
      return findClass(name, DomUtil.getFile(element), element.getModule(), element.getResolveScope(), searchAnonymous);
    }
    return null;
  }

  @NotNull
  private static GlobalSearchScope calcScope(@NotNull PsiFile file, @Nullable Module module) {
    if (module == null) {
      return file.getResolveScope();
    }

    file = file.getOriginalFile();
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return GlobalSearchScope.moduleRuntimeScope(module, true);
    }

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
    return module.getModuleRuntimeScope(fileIndex.isInTestSourceContent(virtualFile));
  }
}
