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
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomJavaUtil {
  private DomJavaUtil() {
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name, @NotNull final PsiFile file, @Nullable final Module module, @Nullable final GlobalSearchScope searchScope) {
    if (name == null) return null;
    if (name.indexOf('$')>=0) name = name.replace('$', '.');

    final GlobalSearchScope scope;
    if (searchScope == null) {

      if (module != null) {
        scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
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

  public static PsiClass findClass(String name, @NotNull DomElement element) {
    XmlElement xmlElement = element.getXmlElement();
    if (xmlElement != null) {
      return findClass(name, (XmlFile)xmlElement.getContainingFile(), element.getModule(), element.getResolveScope());
    }
    return null;
  }
}
