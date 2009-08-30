package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonReferenceProviderTypes;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;

/**
 * @author Dmitry Avdeev
 */
public class CommonReferenceProviderTypesImpl extends CommonReferenceProviderTypes {

  private final JavaClassReferenceProvider myProvider;

  public CommonReferenceProviderTypesImpl(Project project) {
    myProvider = new JavaClassReferenceProvider(project);
  }

  public PsiReferenceProvider getClassReferenceProvider() {
    return myProvider;
  }
}
