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
import com.intellij.codeInspection.bytecodeAnalysis.data.TestConverterData;
import com.intellij.codeInspection.bytecodeAnalysis.data.TestAnnotation;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import org.apache.commons.lang.SystemUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.org.objectweb.asm.Type;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.Arrays;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisTest extends JavaCodeInsightFixtureTestCase {
  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();
  private final String myClassesProjectRelativePath = "/classes/" + Test01.class.getPackage().getName().replace('.', '/');
  private JavaPsiFacade myJavaPsiFacade;
  private InferredAnnotationsManager myInferredAnnotationsManager;
  private BytecodeAnalysisConverter myBytecodeAnalysisConverter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myJavaPsiFacade = JavaPsiFacade.getInstance(myModule.getProject());
    myInferredAnnotationsManager = InferredAnnotationsManager.getInstance(myModule.getProject());
    myBytecodeAnalysisConverter = BytecodeAnalysisConverter.getInstance();
    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      @Override
      public void run() {
        myBytecodeAnalysisConverter.disposeComponent();
      }
    });

    setUpDataClasses();
    setUpVelocityLibrary();
  }

  // TODO: real integration test (possible solution - via external annotation manager??)
  public void testVelocityJar() {
    PsiClass psiClass = myJavaPsiFacade.findClass(SystemUtils.class.getName(), GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);
    PsiMethod getJavaHomeMethod = psiClass.findMethodsByName("getJavaHome", false)[0];
    assertNotNull(InferredAnnotationsManager.getInstance(myModule.getProject()).findInferredAnnotation(getJavaHomeMethod, AnnotationUtil.NOT_NULL));
  }

  private void setUpVelocityLibrary() {
    VirtualFile lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib");
    assertNotNull(lib);
    PsiTestUtil.addLibrary(myModule, "velocity", lib.getPath(), new String[]{"/velocity.jar!/"}, new String[]{});
  }


  public void testInference() throws IOException {
    checkAnnotations(Test01.class);
  }

  public void testConverter() throws IOException {
    checkCompoundIds(Test01.class);
    checkCompoundIds(TestConverterData.class);
    checkCompoundIds(TestConverterData.StaticNestedClass.class);
    checkCompoundIds(TestConverterData.InnerClass.class);
    checkCompoundIds(TestConverterData.GenericStaticNestedClass.class);
    checkCompoundIds(TestAnnotation.class);
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
        for (Annotation parameterAnnotation : parameterAnnotations) {
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

      // contracts
      ExpectContract expectedContract = javaMethod.getAnnotation(ExpectContract.class);
      PsiAnnotation actualContractAnnotation = myInferredAnnotationsManager.findInferredAnnotation(psiMethod, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);

      assertEquals(expectedContract == null, actualContractAnnotation == null);

      if (expectedContract != null) {
        String expectedContractValue = expectedContract.value();
        String actualContractValue = AnnotationUtil.getStringAttributeValue(actualContractAnnotation, null);
        assertEquals(expectedContractValue, actualContractValue);
      }
    }
  }

  private void checkCompoundIds(Class<?> javaClass) throws IOException {
    String javaClassName = javaClass.getCanonicalName();
    System.out.println(javaClassName);
    PsiClass psiClass = myJavaPsiFacade.findClass(javaClassName, GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);

    for (java.lang.reflect.Method javaMethod : javaClass.getDeclaredMethods()) {
      System.out.println(javaMethod.getName());
      Method method = new Method(Type.getType(javaClass).getInternalName(), javaMethod.getName(), Type.getMethodDescriptor(javaMethod));
      boolean noKey = javaMethod.getAnnotation(ExpectNoPsiKey.class) != null;
      PsiMethod psiMethod = psiClass.findMethodsByName(javaMethod.getName(), false)[0];
      checkCompoundId(method, psiMethod, noKey);
    }

    for (Constructor<?> constructor : javaClass.getDeclaredConstructors()) {
      Method method = new Method(Type.getType(javaClass).getInternalName(), "<init>", Type.getConstructorDescriptor(constructor));
      boolean noKey = constructor.getAnnotation(ExpectNoPsiKey.class) != null;
      PsiMethod[] constructors = psiClass.getConstructors();
      PsiMethod psiMethod = constructors[0];
      checkCompoundId(method, psiMethod, noKey);
    }
  }

  private void checkCompoundId(Method method, PsiMethod psiMethod, boolean noKey) throws IOException {
    Direction direction = new Out();
    int[] psiKey = myBytecodeAnalysisConverter.mkCompoundKey(psiMethod, direction);
    if (noKey) {
      assertNull(psiKey);
      return;
    }
    else {
      assertNotNull(psiKey);
    }

    int[] asmKey = myBytecodeAnalysisConverter.mkCompoundKey(new Key(method, direction));

    System.out.println(Arrays.toString(asmKey));
    System.out.println(Arrays.toString(psiKey));

    System.out.println(myBytecodeAnalysisConverter.debugCompoundKey(asmKey));
    System.out.println(myBytecodeAnalysisConverter.debugCompoundKey(psiKey));

    Assert.assertArrayEquals(asmKey, psiKey);
  }

  private void setUpDataClasses() throws IOException {
    File classesDir = new File(Test01.class.getResource(".").getFile());
    File destDir = new File(myModule.getProject().getBaseDir().getPath() + myClassesProjectRelativePath);
    FileUtil.copyDir(classesDir, destDir);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destDir);
    assertNotNull(vFile);
    PsiTestUtil.addLibrary(myModule, "dataClasses", vFile.getPath(), new String[]{""}, ArrayUtil.EMPTY_STRING_ARRAY);
  }

}
