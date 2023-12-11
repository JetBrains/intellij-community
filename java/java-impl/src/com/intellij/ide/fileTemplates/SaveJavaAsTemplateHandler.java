// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates;

import com.intellij.ide.actions.SaveFileAsTemplateHandler;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Nullable;


public final class SaveJavaAsTemplateHandler implements SaveFileAsTemplateHandler {
  @Override
  @Nullable
  public String getTemplateText(final PsiFile psiFile, String fileText, String nameWithoutExtension) {
    if(psiFile instanceof PsiJavaFile javaFile){
      String packageName = javaFile.getPackageName();
      if (packageName.length() > 0){
        fileText = StringUtil.replace(fileText, packageName, "${PACKAGE_NAME}");
      }
      PsiClass[] classes = javaFile.getClasses();
      PsiClass psiClass = null;
      for (PsiClass aClass : classes) {
        if (nameWithoutExtension.equals(aClass.getName())) {
          psiClass = aClass;
          break;
        }
      }
      if(psiClass != null){
        //todo[myakovlev] ? PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
        return StringUtil.replace(fileText, nameWithoutExtension,"${NAME}");
      }
    }
    return null;
  }
}
