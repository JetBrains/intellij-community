package com.intellij.internal.psiView;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public interface PsiViewerExtension {
  ExtensionPointName<PsiViewerExtension> EP_NAME = ExtensionPointName.create("com.intellij.psiViewerExtension");

  String getName();
  PsiElement createElement(Project project, String text);
}
