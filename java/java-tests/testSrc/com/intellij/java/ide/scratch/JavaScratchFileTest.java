// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.scratch;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.ide.scratch.ScratchFileCreationHelper;
import com.intellij.ide.scratch.ScratchFileCreator;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaScratchFileTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Sdk jdk = ModuleRootManager.getInstance(fixture.getModule()).getSdk();
    WriteAction.runAndWait(() -> ProjectRootManager.getInstance(fixture.getProject()).setProjectSdk(jdk));
  }

  @Test
  public void testEmptyProject() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    ModifiableModuleModel model = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
    model.disposeModule(fixture.getModule());
    WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> model.commit());
    
    ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
    context.language = JavaLanguage.INSTANCE;
    PsiFile file = EdtTestUtil.runInEdtAndGet(() -> ScratchFileCreator.doCreateNewScratch(fixture.getProject(), context));
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiJavaFile.class, file);
      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      assertEquals(1, classes.length);
      PsiMethod[] methods = classes[0].getMethods();
      assertEquals(1, methods.length);
      PsiParameterList parameterList = methods[0].getParameterList();
      assertEquals(1, parameterList.getParametersCount());
      PsiType type = parameterList.getParameter(0).getType();
      assertInstanceOf(PsiArrayType.class, type);
      type = ((PsiArrayType)type).getComponentType();
      assertInstanceOf(PsiClassType.class, type);
      PsiClass stringClass = ((PsiClassType)type).resolve();
      assertNotNull(stringClass);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, stringClass.getQualifiedName());
    });
  }
}
