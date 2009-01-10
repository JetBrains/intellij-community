package com.intellij.codeInsight.runner;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

/**
 * @author ilyas
 */
public interface JavaMainMethodProvider {

  ExtensionPointName<JavaMainMethodProvider> EP_NAME = ExtensionPointName.create("com.intellij.javaMainMethodProvider");

  boolean isApplicable(PsiClass clazz);

  boolean hasMainMethod(PsiClass clazz);

  PsiMethod findMainInClass(final PsiClass clazz);

}
