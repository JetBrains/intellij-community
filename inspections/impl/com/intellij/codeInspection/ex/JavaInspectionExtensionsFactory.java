/*
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.lang.HTMLComposerExtension;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.lang.RefManagerExtension;
import com.intellij.codeInspection.reference.RefJavaManagerImpl;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class JavaInspectionExtensionsFactory extends InspectionExtensionsFactory {

  public GlobalInspectionContextExtension createGlobalInspectionContextExtension() {
    return new GlobalJavaInspectionContextImpl();
  }

  public RefManagerExtension createRefManagerExtension(final RefManager refManager) {
    return new RefJavaManagerImpl((RefManagerImpl)refManager);
  }

  public HTMLComposerExtension createHTMLComposerExtension(final HTMLComposer composer) {
    return new HTMLJavaHTMLComposerImpl((HTMLComposerImpl)composer);
  }

  public boolean isToCheckMember(final PsiElement element, final String id) {
    return SuppressManager.getInstance().getElementToolSuppressedIn(element, id) == null;
  }

  @Nullable
  public String getSuppressedInspectionIdsIn(final PsiElement element) {
    return SuppressManager.getInstance().getSuppressedInspectionIdsIn(element);
  }

  public boolean isProjectConfiguredToRunInspections(final Project project, final boolean online) {
    return GlobalJavaInspectionContextImpl.isInspectionsEnabled(online, project);
  }
}