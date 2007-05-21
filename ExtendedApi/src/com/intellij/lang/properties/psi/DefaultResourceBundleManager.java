/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 05-Feb-2007
 */
package com.intellij.lang.properties.psi;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.SetupJDKFix;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

public class DefaultResourceBundleManager extends ResourceBundleManager {
  public DefaultResourceBundleManager(final Project project) {
    super(project);
  }

  @Nullable
  public PsiClass getResourceBundle() {
    return PsiManager.getInstance(myProject).findClass("java.util.ResourceBundle", GlobalSearchScope.allScope(myProject));
  }

  public String getTemplateName() {
    return FileTemplateManager.TEMPLATE_I18NIZED_EXPRESSION;
  }

  public String getConcatenationTemplateName() {
    return FileTemplateManager.TEMPLATE_I18NIZED_CONCATENATION;
  }

  public boolean isActive(PsiFile context) throws ResourceBundleNotFoundException{
    if (getResourceBundle() != null) {
      return true;
    }
    throw new ResourceBundleNotFoundException(CodeInsightBundle.message("i18nize.dialog.error.jdk.message"), SetupJDKFix.getInstnace());
  }

  public boolean canShowJavaCodeInfo() {
    return true;
  }
}