/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.jsp;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author peter
 */
public abstract class JspManager {

  public static final Key<VirtualFile[]> DIRECTORIES_KEY = Key.create("TagDirOriginalDirs");
  public static final  @NonNls String TAG_DIR_NS_PREFIX = "urn:jsptagdir:";

  public static JspManager getInstance(@NotNull Project project) {
    return project.getComponent(JspManager.class);
  }

  @NotNull
  public abstract Set<String> getNamespacesByTagName(@NotNull String tagName, @NotNull JspFile context, final boolean showProgress);
  @NotNull
  public abstract Set<String> getNamespacesByFunctionName(@NotNull String tagName, @NotNull JspFile context, final boolean showProgress);

  /**
   * Returns possible tag names for given context JSP file.
   * @param context context JSP file.
   * @return map from tag name to the list of namespaces where it is defined.
   */
  public abstract Set<String> getAvailableTagNames(@NotNull JspFile context);

  @Nullable
  public abstract String getPrefixForNamespace(@NotNull String namespaceUri, final @NotNull JspFile context);

  @Nullable
  public abstract String getDefaultPrefix(@NotNull XmlFile taglibFile);

  public abstract String[] getPossibleTldUris(JspFile file);

  public abstract String[] getPossibleTldUris(@NotNull Module module);

  public abstract Collection<XmlFile> getPossibleTldFiles(@NotNull Module module);

  @Nullable
  public abstract XmlFile getTldFileByUri(String uri, @NotNull JspFile jspFile);

  @Nullable
  public abstract XmlFile getTldFileByUri(String uri, @Nullable Module module, @Nullable JspFile jspFile);

  @Nullable
  public abstract XmlElementDescriptor getDirectiveDescriptorByName(String name, final @NotNull PsiFile context);

  @Nullable
  public abstract XmlNSDescriptor getActionsLibrary(final @NotNull PsiFile context);

  public abstract boolean isJsp_2_1_OrBetter(final @NotNull PsiFile context);
}
