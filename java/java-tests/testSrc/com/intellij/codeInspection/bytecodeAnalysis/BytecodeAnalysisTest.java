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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.InferredAnnotationsManager;
import com.intellij.codeInspection.bytecodeAnalysis.data.Test01;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisTest extends JavaCodeInsightFixtureTestCase {

  private final String myClassesProjectRelativePath = "/classes/" + Test01.class.getPackage().getName().replace('.', '/');
  private JavaPsiFacade myJavaPsiFacade;
  private InferredAnnotationsManager myInferredAnnotationsManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myJavaPsiFacade = JavaPsiFacade.getInstance(myModule.getProject());
    myInferredAnnotationsManager = InferredAnnotationsManager.getInstance(myModule.getProject());
  }

  /*
  public void testVelocityJar() {
    VirtualFile lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib");
    assertNotNull(lib);
    PsiTestUtil.addLibrary(myModule, "velocity", lib.getPath(), new String[]{"/velocity.jar!/"}, new String[]{});
    PsiClass psiClass = myJavaPsiFacade.findClass(SystemUtils.class.getName(), GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);
    PsiMethod getJavaHomeMethod = psiClass.findMethodsByName("getJavaHome", false)[0];
    assertNotNull(InferredAnnotationsManager.getInstance(myModule.getProject()).findInferredAnnotation(getJavaHomeMethod, AnnotationUtil.NOT_NULL));
  }
  */

  public void testDataClasses() throws IOException {
    VirtualFile dataClassesDir = setUpDataClasses();
    PsiTestUtil.addLibrary(myModule, "dataClasses", dataClassesDir.getPath(), new String[]{""}, ArrayUtil.EMPTY_STRING_ARRAY);
    //PsiClass psiClass = myJavaPsiFacade.findClass(Test01.class.getName(), GlobalSearchScope.moduleWithLibrariesScope(myModule));
    //assertNotNull(psiClass);

    checkAnnotations(Test01.class);
  }

  private void checkAnnotations(Class<?> javaClass) {
    PsiClass psiClass = myJavaPsiFacade.findClass(javaClass.getName(), GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);

    for (java.lang.reflect.Method javaMethod : javaClass.getDeclaredMethods()) {
      PsiMethod psiMethod = psiClass.findMethodsByName(javaMethod.getName(), false)[0];
      Annotation[][] annotations = javaMethod.getParameterAnnotations();

      // not-null parameters
      params: for (int i = 0; i < annotations.length; i++) {
        Annotation[] parameterAnnotations = annotations[i];
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[i];
        PsiAnnotation inferredAnnotation = myInferredAnnotationsManager.findInferredAnnotation(psiParameter, AnnotationUtil.NOT_NULL);
        for (int j = 0; j < parameterAnnotations.length; j++) {
          Annotation parameterAnnotation = parameterAnnotations[j];
          if (parameterAnnotation.annotationType() == ExpectNotNull.class) {
            assertNotNull(inferredAnnotation);
            continue params;
          }
        }
        assertNull(inferredAnnotation);
      }

      // not-null result
      ExpectNotNull expectedAnnotation = javaMethod.getAnnotation(ExpectNotNull.class);
      PsiAnnotation actualAnnotation = myInferredAnnotationsManager.findInferredAnnotation(psiMethod, AnnotationUtil.NOT_NULL);
      assertEquals(expectedAnnotation == null, actualAnnotation == null);
    }
  }

  private VirtualFile setUpDataClasses() throws IOException {
    File classesDir = new File(Test01.class.getResource(".").getFile());
    File destDir = new File(myModule.getProject().getBaseDir().getPath() + myClassesProjectRelativePath);
    FileUtil.copyDir(classesDir, destDir);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destDir);
    assertNotNull(vFile);
    return vFile;
  }

}
