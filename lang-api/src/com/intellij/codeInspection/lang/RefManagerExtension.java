/*
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public interface RefManagerExtension<T> {
  Key<T> getID();

  Language getLanguage();

  void iterate(RefVisitor visitor);

  void cleanup();

  void removeReference(RefElement refElement);

  @Nullable
  RefElement createRefElement(PsiElement psiElement);

  @Nullable
  RefEntity getReference(final String type, final String fqName);

  @Nullable
  String getType(RefEntity entity);

  RefEntity getRefinedElement(final RefEntity ref);

  void visitElement(final PsiElement element);

  @Nullable
  String getGroupName(final RefEntity entity);

  boolean belongsToScope(final PsiElement psiElement);

  void export(final RefEntity refEntity, final Element element);
}