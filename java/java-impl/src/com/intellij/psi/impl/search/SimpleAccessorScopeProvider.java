// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

public final class SimpleAccessorScopeProvider implements CustomPropertyScopeProvider {
  @Override
  public @NotNull SearchScope getScope(final @NotNull Project project) {
    return GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(project),
                                                           StdFileTypes.JSP, StdFileTypes.JSPX,
                                                           XmlFileType.INSTANCE, XHtmlFileType.INSTANCE);
  }
}
