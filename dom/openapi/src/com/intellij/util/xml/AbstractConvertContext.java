/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class AbstractConvertContext extends ConvertContext {

  public final PsiClass findClass(String name) {
    if (name == null) return null;
    final XmlFile file = getFile();
    if (name.indexOf('$')>=0) name = name.replace('$', '.');

    final Module module = getModule();
    final PsiClass aClass;
    final XmlElement element = getInvocationElement().getXmlElement();
    if (module == null && element != null && getPsiManager().isInProject(element)) {
      final GlobalSearchScope baseScope = GlobalSearchScope.allScope(getPsiManager().getProject());
      aClass = getPsiManager().findClass(name, new GlobalSearchScope() {

        public boolean contains(VirtualFile file) {
          return baseScope.contains(file);
        }

        public int compare(VirtualFile file1, VirtualFile file2) {
          return baseScope.compare(file1, file2);
        }

        public boolean isSearchInModuleContent(Module aModule) {
          return false;
        }

        public boolean isSearchInLibraries() {
          return true;
        }
      });
    }
    else if (module != null) {
      aClass = getPsiManager().findClass(name, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
    }
    else {
      aClass = getPsiManager().findClass(name, file.getResolveScope());
    }
    if (aClass != null) {
      assert aClass.isValid() : name;
    }
    return aClass;
  }

  public final XmlTag getTag() {
    return getInvocationElement().getXmlTag();
  }

  @NotNull
  public final XmlFile getFile() {
    return getInvocationElement().getRoot().getFile();
  }

  public Module getModule() {
    return getInvocationElement().getModule();
  }
}
