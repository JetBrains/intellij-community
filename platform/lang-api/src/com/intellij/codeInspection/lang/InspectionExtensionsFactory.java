/*
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public abstract class InspectionExtensionsFactory {

  public static final ExtensionPointName<InspectionExtensionsFactory> EP_NAME = ExtensionPointName.create("com.intellij.codeInspection.InspectionExtension");

  public abstract GlobalInspectionContextExtension createGlobalInspectionContextExtension();
  public abstract RefManagerExtension createRefManagerExtension(RefManager refManager);
  public abstract HTMLComposerExtension createHTMLComposerExtension(final HTMLComposer composer);

  public abstract boolean isToCheckMember(PsiElement element, String id);

  @Nullable
  public abstract String getSuppressedInspectionIdsIn(PsiElement element);

  public abstract boolean isProjectConfiguredToRunInspections(Project project, boolean online);

}