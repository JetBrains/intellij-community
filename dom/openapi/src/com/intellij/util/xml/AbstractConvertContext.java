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
    if (module != null) {
      aClass = file.getManager().findClass(name, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
    }
    else {
      aClass = file.getManager().findClass(name, file.getResolveScope());
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
