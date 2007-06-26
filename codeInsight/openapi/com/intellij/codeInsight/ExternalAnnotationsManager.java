/*
 * User: anna
 * Date: 26-Jun-2007
 */
package com.intellij.codeInsight;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class ExternalAnnotationsManager {
  @NonNls public static final String ANNOTATIONS_XML = "annotations.xml";

  public static ExternalAnnotationsManager getInstance() {
    return ServiceManager.getService(ExternalAnnotationsManager.class);
  }

  @Nullable
  public abstract PsiAnnotation findExternalAnnotation(final PsiModifierListOwner listOwner, final String annotationFQN);

  public abstract void annotateExternally(final PsiModifierListOwner listOwner, final String annotationFQName);

  public abstract boolean useExternalAnnotations(final PsiFile file);

}