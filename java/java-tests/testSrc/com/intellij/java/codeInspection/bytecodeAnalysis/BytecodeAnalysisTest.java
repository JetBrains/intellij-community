/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.bytecodeAnalysis.*;
import com.intellij.codeInspection.bytecodeAnalysis.asm.LeakingParameters;
import com.intellij.java.codeInspection.bytecodeAnalysis.data.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.junit.Assert;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Stream;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisTest extends JavaCodeInsightFixtureTestCase {
  public static final String ORG_JETBRAINS_ANNOTATIONS_CONTRACT = Contract.class.getName();
  private static final String PACKAGE_PATH = Test01.class.getPackage().getName().replace('.', '/');
  private JavaPsiFacade myJavaPsiFacade;
  private ProjectBytecodeAnalysis myBytecodeAnalysisService;
  private MessageDigest myMessageDigest;


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myJavaPsiFacade = JavaPsiFacade.getInstance(myModule.getProject());
    myBytecodeAnalysisService = ProjectBytecodeAnalysis.getInstance(myModule.getProject());
    myMessageDigest = MessageDigest.getInstance("MD5");
    setUpDataClasses();
  }

  @Override
  protected void tearDown() throws Exception {
    myJavaPsiFacade = null;
    myBytecodeAnalysisService = null;
    myMessageDigest = null;
    super.tearDown();
  }

  public void testInference() {
    checkAnnotations(Test01.class);
    checkAnnotations(Test02.class);
    checkAnnotations(TestNonStable.class);
    checkAnnotations(TestConflict.class);
    checkAnnotations(TestEnum.class);
  }

  public void testHashCollision() {
    checkAnnotations(TestHashCollision.class);
  }

  public void testConverter() {
    checkCompoundIds(Test01.class);
    checkCompoundIds(TestConverterData.class);
    checkCompoundIds(TestConverterData.StaticNestedClass.class);
    checkCompoundIds(TestConverterData.InnerClass.class);
    checkCompoundIds(TestConverterData.GenericStaticNestedClass.class);
    checkCompoundIds(TestAnnotation.class);
  }

  public void testLeakingParametersAnalysis() throws IOException {
    checkLeakingParameters(LeakingParametersData.class);
  }

  private static void checkLeakingParameters(Class<?> jClass) throws IOException {
    final HashMap<Method, boolean[]> map = new HashMap<>();

    // collecting leakedParameters
    final ClassReader classReader = new ClassReader(jClass.getResourceAsStream("/" + jClass.getName().replace('.', '/') + ".class"));
    classReader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final MethodNode node = new MethodNode(Opcodes.API_VERSION, access, name, desc, signature, exceptions);
        final Method method = new Method(classReader.getClassName(), name, desc);
        return new MethodVisitor(Opcodes.API_VERSION, node) {
          @Override
          public void visitEnd() {
            super.visitEnd();
            try {
              map.put(method, LeakingParameters.build(classReader.getClassName(), node, false).parameters);
            }
            catch (AnalyzerException ignore) {}
          }
        };
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

    for (java.lang.reflect.Method jMethod : jClass.getDeclaredMethods()) {
      Method method = new Method(Type.getType(jClass).getInternalName(), jMethod.getName(), Type.getMethodDescriptor(jMethod));
      Annotation[][] annotations = jMethod.getParameterAnnotations();
      for (int i = 0; i < annotations.length; i++) {
        boolean isLeaking = false;
        Annotation[] parameterAnnotations = annotations[i];
        for (Annotation parameterAnnotation : parameterAnnotations) {
          if (parameterAnnotation.annotationType() == ExpectLeaking.class) {
            isLeaking = true;
          }
        }
        assertEquals(method.toString() + " #" + i, isLeaking, map.get(method)[i]);
      }
    }
  }

  private void checkAnnotations(Class<?> javaClass) {
    PsiClass psiClass = myJavaPsiFacade.findClass(javaClass.getName(), GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);

    for (java.lang.reflect.Method javaMethod : javaClass.getDeclaredMethods()) {
      if(javaMethod.isSynthetic()) continue; // skip lambda runtime representation
      PsiMethod psiMethod = ArrayUtil.getFirstElement(psiClass.findMethodsByName(javaMethod.getName(), false));
      if (psiMethod == null) {
        // Enum compilation adds some methods to bytecode which are not marked as synthetic
        if(javaClass.isEnum()) continue;
        fail("Unable to find method "+javaMethod.getName()+" in bytecode");
      }
      Annotation[][] annotations = javaMethod.getParameterAnnotations();

      // not-null parameters
      for (int i = 0; i < annotations.length; i++) {
        Annotation[] parameterAnnotations = annotations[i];
        PsiParameter psiParameter = psiMethod.getParameterList().getParameters()[i];
        PsiAnnotation inferredAnnotation = myBytecodeAnalysisService.findInferredAnnotation(psiParameter, AnnotationUtil.NOT_NULL);
        boolean expectNotNull = Stream.of(parameterAnnotations).anyMatch(anno -> anno.annotationType() == ExpectNotNull.class);
        assertNullity(getMethodDisplayName(javaMethod) + "/arg#" + i, expectNotNull, inferredAnnotation);
      }

      // not-null result
      ExpectNotNull expectedAnnotation = javaMethod.getAnnotation(ExpectNotNull.class);
      PsiAnnotation actualAnnotation = myBytecodeAnalysisService.findInferredAnnotation(psiMethod, AnnotationUtil.NOT_NULL);
      assertNullity(getMethodDisplayName(javaMethod), expectedAnnotation != null, actualAnnotation);

      // contracts
      ExpectContract expectedContract = javaMethod.getAnnotation(ExpectContract.class);
      PsiAnnotation actualContract = myBytecodeAnalysisService.findInferredAnnotation(psiMethod, ORG_JETBRAINS_ANNOTATIONS_CONTRACT);

      String expectedText = expectedContract == null ? "null" : expectedContract.toString();
      String inferredText = actualContract == null ? "null" : actualContract.getText();

      assertEquals(getMethodDisplayName(javaMethod) + ":" + expectedText + " <> " + inferredText,
                   expectedContract == null, actualContract == null);

      if (expectedContract != null && actualContract != null) {
        String expectedContractValue = expectedContract.value();
        String actualContractValue = AnnotationUtil.getStringAttributeValue(actualContract, null);
        assertEquals(getMethodDisplayName(javaMethod), expectedContractValue, actualContractValue);

        boolean expectedPureValue = expectedContract.pure();
        boolean actualPureValue = getPureAttribute(actualContract);
        assertEquals(getMethodDisplayName(javaMethod), expectedPureValue, actualPureValue);
      }
    }
  }

  private static void assertNullity(String message, boolean expectedNotNull, PsiAnnotation inferredAnnotation) {
    if(expectedNotNull && inferredAnnotation == null) {
      fail(message+": @NotNull expected, but not inferred");
    } else if(!expectedNotNull && inferredAnnotation != null) {
      fail(message+": @NotNull inferred, but not expected");
    }
  }

  private static String getMethodDisplayName(java.lang.reflect.Method javaMethod) {
    return javaMethod.getDeclaringClass().getSimpleName()+"."+javaMethod.getName();
  }

  private static boolean getPureAttribute(PsiAnnotation annotation) {
    Boolean pureValue = AnnotationUtil.getBooleanAttributeValue(annotation, "pure");
    return pureValue != null && pureValue.booleanValue();
  }

  private void checkCompoundIds(Class<?> javaClass) {
    String javaClassName = javaClass.getCanonicalName();
    PsiClass psiClass = myJavaPsiFacade.findClass(javaClassName, GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);

    for (java.lang.reflect.Method javaMethod : javaClass.getDeclaredMethods()) {
      if(javaMethod.isSynthetic()) continue; // skip lambda runtime representation
      Method method = new Method(Type.getType(javaClass).getInternalName(), javaMethod.getName(), Type.getMethodDescriptor(javaMethod));
      boolean noKey = javaMethod.getAnnotation(ExpectNoPsiKey.class) != null;
      PsiMethod[] methods = psiClass.findMethodsByName(javaMethod.getName(), false);
      assertTrue("Must be single method: "+javaMethod, methods.length == 1);
      PsiMethod psiMethod = methods[0];
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

  private void checkCompoundId(Method method, PsiMethod psiMethod, boolean noKey) {
    /*
    System.out.println();
    System.out.println(method.internalClassName);
    System.out.println(method.methodName);
    System.out.println(method.methodDesc);
    */


    EKey psiKey = BytecodeAnalysisConverter.psiKey(psiMethod, Direction.Out);
    if (noKey) {
      assertTrue(null == psiKey);
      return;
    }
    else {
      assertFalse(null == psiKey);
    }
    EKey asmKey = new EKey(method, Direction.Out, true);
    Assert.assertEquals(asmKey, psiKey);
    Assert.assertEquals(asmKey.hashed(myMessageDigest), psiKey.hashed(myMessageDigest));
  }

  private void setUpDataClasses() throws Exception {
    File classesDir = new File(Test01.class.getResource("/" + PACKAGE_PATH).toURI());
    String basePath = myModule.getProject().getBaseDir().getPath();
    File destDir = new File(basePath + "/classes/" + PACKAGE_PATH);
    FileUtil.copyDir(classesDir, destDir);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destDir);
    assertNotNull(vFile);
    PsiTestUtil.addLibrary(myModule, "dataClasses", vFile.getPath(), new String[]{""}, ArrayUtil.EMPTY_STRING_ARRAY);

    if(getTestName(false).equals("Inference")) {
      setUpConflictingClasses(basePath);
    }
  }

  private void setUpConflictingClasses(String basePath) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null);
    File sourcePath = new File(PlatformTestUtil.getCommunityPath() + "/java/java-tests/testSrc/" + PACKAGE_PATH);
    File[] sourceFiles = new File(sourcePath.getParentFile(), "classConflict").listFiles((dir, name) -> name.endsWith(".java"));
    assertNotNull(sourceFiles);
    Iterable<? extends JavaFileObject> sources = manager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFiles));
    File conflictOutput = new File(basePath + "/conflict/");
    assertTrue(conflictOutput.mkdirs());
    manager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(conflictOutput));
    JavaCompiler.CompilationTask task = compiler.getTask(null, manager, diagnostics, null, null, sources);
    if(!task.call()) {
      fail(diagnostics.getDiagnostics().toString());
    }
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(conflictOutput);
    assertNotNull(vFile);
    PsiTestUtil.addLibrary(myModule, "conflictClasses", vFile.getPath(), new String[]{""}, ArrayUtil.EMPTY_STRING_ARRAY);
  }
}
