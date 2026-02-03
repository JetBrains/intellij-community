// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jsp;

import com.intellij.lang.jsp.IBaseJspManager;
import com.intellij.lang.jsp.JspVersion;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public abstract class JspManager implements IBaseJspManager {

  public static JspManager getInstance(@NotNull Project project) {
    return project.getService(JspManager.class);
  }

  public abstract @NotNull Set<String> getNamespacesByTagName(@NotNull String tagName, @NotNull JspFile context, final boolean showProgress);
  public abstract @NotNull Set<String> getNamespacesByFunctionName(@NotNull String tagName, @NotNull JspFile context, final boolean showProgress);

  /**
   * Returns possible tag names for given context JSP file.
   * @param context context JSP file.
   * @return set of tag names
   */
  public abstract @NotNull MultiMap<String,String> getAvailableTagNames(final @NotNull JspFile context);

  public abstract @NotNull List<Pair<String,String>> getAvailableFunctions(final @NotNull JspFile context);

  public abstract @Nullable String getPrefixForNamespace(@NotNull String namespaceUri, final @NotNull JspFile context);

  public abstract @Nullable String getDefaultPrefix(@NotNull XmlFile taglibFile);

  public abstract String[] getPossibleTldUris(JspFile file);

  public abstract String[] getPossibleTldUris(@NotNull Module module);

  public abstract @NotNull @Unmodifiable Collection<XmlFile> getPossibleTldFiles(@NotNull Module module);

  public abstract @Nullable String getTaglibUri(@NotNull XmlFile taglibFile);

  public abstract @Nullable XmlFile getTldFileByUri(@NonNls String uri, @NotNull JspFile jspFile);

  public abstract @Nullable XmlFile getTldFileByUri(@NonNls String uri, @Nullable Module module, @Nullable JspFile jspFile);

  public abstract @NotNull JspVersion getJspVersion(@NotNull PsiFileSystemItem context);
}
