/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiClass;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.module.Module;

/**
 * @author peter
 */
public class DomJavaUtil {
  private DomJavaUtil() {
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

    final PsiClass aClass = JavaPsiFacade.getInstance(file.getProject()).findClass(name, scope);
    if (aClass != null) {
      assert aClass.isValid() : name;
    }
    return aClass;
  }
}
