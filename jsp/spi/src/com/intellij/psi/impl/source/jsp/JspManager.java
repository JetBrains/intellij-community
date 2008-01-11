/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.jsp;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

/**
 * @author peter
 */
public abstract class JspManager {
  public static final Key<VirtualFile[]> DIRECTORIES_KEY = Key.create("TagDirOriginalDirs");
  public static final  @NonNls String TAG_DIR_NS_PREFIX = "urn:jsptagdir:";

  public static JspManager getInstance(@NotNull Project project) {
    return project.getComponent(JspManager.class);
  }

  @Nullable
  public abstract String getTaglibUri(final @NotNull XmlFile taglibFile);

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
  
  public abstract ModificationTracker getRootsModificationTracker();

  public abstract XmlFile getImplicitXmlTagLibraryFile();

  public abstract boolean isJsp_2_1_OrBetter(final @NotNull PsiFile context);
}
