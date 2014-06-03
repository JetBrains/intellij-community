/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.apache.commons.lang.SystemUtils;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisTest extends JavaCodeInsightFixtureTestCase {

  public void testVelocityJar() {
    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(myModule.getProject());
    VirtualFile lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib");
    assertNotNull(lib);
    PsiTestUtil.addLibrary(myModule, "velocity", lib.getPath(), new String[]{"/velocity.jar!/"}, new String[]{});
    PsiClass psiClass = javaFacade.findClass(SystemUtils.class.getName(), GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);
    PsiMethod getJavaHomeMethod = psiClass.findMethodsByName("getJavaHome", false)[0];
    assertNotNull(InferredAnnotationsManager.getInstance(myModule.getProject()).findInferredAnnotation(getJavaHomeMethod, AnnotationUtil.NOT_NULL));
  }
}
