/*
 * User: anna
 * Date: 26-Jun-2007
 */
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class ExternalAnnotationsManager {
  @NonNls public static final String ANNOTATIONS_XML = "annotations.xml";

  public static ExternalAnnotationsManager getInstance(Project project) {
    return ServiceManager.getService(project, ExternalAnnotationsManager.class);
  }

  @Nullable
  public abstract PsiAnnotation findExternalAnnotation(final PsiModifierListOwner listOwner, final String annotationFQN);

  @Nullable
  public abstract PsiAnnotation[] findExternalAnnotations(final PsiModifierListOwner listOwner);

  public abstract void annotateExternally(final PsiModifierListOwner listOwner, final String annotationFQName);

  public abstract boolean deannotate(final PsiModifierListOwner listOwner, final String annotationFQN);

  public abstract boolean useExternalAnnotations(final PsiElement element);

}