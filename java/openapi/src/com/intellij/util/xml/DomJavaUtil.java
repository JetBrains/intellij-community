/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomJavaUtil {
  private DomJavaUtil() {
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name, @NotNull PsiFile file, @Nullable final Module module, @Nullable final GlobalSearchScope searchScope) {
    if (name == null) return null;
    if (name.indexOf('$')>=0) name = name.replace('$', '.');

    final GlobalSearchScope scope;
    if (searchScope == null) {

      if (module != null) {
        file = file.getOriginalFile();
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
          scope = GlobalSearchScope.moduleRuntimeScope(module, true);
        }
        else {
          ProjectFileIndex fileIndex = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
          boolean tests = fileIndex.isInTestSourceContent(virtualFile);
          scope = module.getModuleRuntimeScope(tests);
        }
      }
      else {
        scope = file.getResolveScope();
      }
    }
    else {
      scope = searchScope;
    }

    final PsiClass aClass = JavaPsiFacade.getInstance(file.getProject()).findClass(name, scope);
    if (aClass != null) {
      assert aClass.isValid() : name;
    }
    return aClass;
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name, @NotNull DomElement element) {
    assert element.isValid();
    XmlElement xmlElement = element.getXmlElement();
    if (xmlElement != null) {
      assert xmlElement.isValid();
      return findClass(name, xmlElement.getContainingFile(), element.getModule(), element.getResolveScope());
    }
    return null;
  }
}
