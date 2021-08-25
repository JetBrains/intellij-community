// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.*;
import com.intellij.testFramework.JavaProjectTestCase;

public class JavaScratchFileTest extends JavaProjectTestCase {
  @Override
  protected void setUpJdk() {
    super.setUpJdk();
    ProjectRootManager.getInstance(getProject()).setProjectSdk(getTestProjectJdk());
  }

  public void testEmptyProject() {
    ModifiableModuleModel model = ModuleManager.getInstance(getProject()).getModifiableModel();
    model.disposeModule(getModule());
    WriteCommandAction.runWriteCommandAction(getProject(), () -> model.commit());
    
    ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
    context.language = JavaLanguage.INSTANCE;
    PsiFile file = ScratchFileActions.doCreateNewScratch(getProject(), context);
    assertInstanceOf(file, PsiJavaFile.class);
    PsiClass[] classes = ((PsiJavaFile)file).getClasses();
    assertEquals(1, classes.length);
    PsiMethod[] methods = classes[0].getMethods();
    assertEquals(1, methods.length);
    PsiParameterList parameterList = methods[0].getParameterList();
    assertEquals(1, parameterList.getParametersCount());
    PsiType type = parameterList.getParameter(0).getType();
    assertInstanceOf(type, PsiArrayType.class);
    type = ((PsiArrayType)type).getComponentType();
    assertInstanceOf(type, PsiClassType.class);
    PsiClass stringClass = ((PsiClassType)type).resolve();
    assertNotNull(stringClass);
    assertEquals(CommonClassNames.JAVA_LANG_STRING, stringClass.getQualifiedName());
  }
}
