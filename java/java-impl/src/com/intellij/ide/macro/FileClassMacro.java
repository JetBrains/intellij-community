// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

public final class FileClassMacro extends Macro {
  @Override
  public String getName() {
    return "FileClass";
  }

  @Override
  public String getDescription() {
    return IdeBundle.message("macro.class.name");
  }

  @Override
  public String expand(DataContext dataContext) {
    //Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    //if (project == null) {
    //  return null;
    //}
    //VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    //if (file == null) {
    //  return null;
    //}
    //PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    //if (!(psiFile instanceof PsiJavaFile)) {
    //  return null;
    //}
    final PsiFile javaFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (!(javaFile instanceof PsiJavaFile)) return null;
    PsiClass[] classes = ((PsiJavaFile) javaFile).getClasses();
    if (classes.length == 1) {
      return classes[0].getQualifiedName();
    }
    String fileName = javaFile.getVirtualFile().getNameWithoutExtension();
    for (PsiClass aClass : classes) {
      String name = aClass.getName();
      if (fileName.equals(name)) {
        return aClass.getQualifiedName();
      }
    }
    return null;
  }
}
