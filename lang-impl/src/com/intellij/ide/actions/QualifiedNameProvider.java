package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface QualifiedNameProvider {
  ExtensionPointName<QualifiedNameProvider> EP_NAME = ExtensionPointName.create("com.intellij.qualifiedNameProvider");

  @Nullable
  PsiElement adjustElementToCopy(PsiElement element);

  @Nullable
  String getQualifiedName(PsiElement element);

  PsiElement qualifiedNameToElement(final String fqn, final Project project);

  void insertQualifiedName(final String fqn, final PsiElement element, final Editor editor, final Project project);
}
