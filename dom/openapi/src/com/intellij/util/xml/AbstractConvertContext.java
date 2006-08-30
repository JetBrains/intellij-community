/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class AbstractConvertContext extends ConvertContext {

  public final PsiClass findClass(String name, @Nullable final GlobalSearchScope searchScope) {
    if (name == null) return null;
    final XmlFile file = getFile();
    if (name.indexOf('$')>=0) name = name.replace('$', '.');

    final GlobalSearchScope scope;
    if (searchScope == null) {
      final Module module = getModule();
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
    final PsiClass aClass = getPsiManager().findClass(name, scope);
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
