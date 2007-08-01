/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class AbstractConvertContext extends ConvertContext {

  public final PsiClass findClass(@Nullable String name, @Nullable final GlobalSearchScope searchScope) {
    return findClass(name, getFile(), getModule(), searchScope);
  }

  @Nullable
  public static PsiClass findClass(@Nullable String name, @NotNull final XmlFile file, @Nullable final Module module, @Nullable final GlobalSearchScope searchScope) {
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

    final PsiClass aClass = file.getManager().findClass(name, scope);
    if (aClass != null) {
      assert aClass.isValid() : name;
    }
    return aClass;
  }

  public final XmlTag getTag() {
    return getInvocationElement().getXmlTag();
  }

  @Nullable
  public XmlElement getXmlElement() {
    return getInvocationElement().getXmlElement();
  }

  @NotNull
  public final XmlFile getFile() {
    return getInvocationElement().getRoot().getFile();
  }

  public Module getModule() {
    return getInvocationElement().getRoot().getRootElement().getModule();
  }

  public PsiManager getPsiManager() {
    return getFile().getManager();
  }
}
