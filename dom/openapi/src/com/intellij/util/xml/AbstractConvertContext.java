/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * @author peter
 */
public abstract class AbstractConvertContext extends ConvertContext {

  public final PsiClass findClass(String name) {
    if (name == null) return null;
    final XmlFile file = getFile();
    if (name.indexOf('$')>=0) name = name.replace('$', '.');

    // find module-based classes first, if available
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module != null) {
      final PsiClass aClass = file.getManager().findClass(name, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
      if (aClass != null) return aClass;
    }
    return file.getManager().findClass(name, file.getResolveScope());
  }

  public final XmlTag getTag() {
    return getInvocationElement().getXmlTag();
  }

  public final XmlFile getFile() {
    return getInvocationElement().getRoot().getFile();
  }

  public Module getModule() {
    return getInvocationElement().getModule();
  }
}
