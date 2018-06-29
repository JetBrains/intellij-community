// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisTest extends JavaCodeInsightFixtureTestCase {
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
    checkAnnotations(Test01.class.getName());
    checkAnnotations(Test02.class.getName());
    checkAnnotations(TestNonStable.class.getName());
    checkAnnotations(TestConflict.class.getName());
    checkAnnotations(TestEnum.class.getName());
  }

  public void testJava9Inference() {
    checkAnnotations(Test01.class.getPackage().getName()+".TestJava9");
  }

  public void testHashCollision() {
    checkAnnotations(TestHashCollision.class.getName());
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
    final Map<Member, boolean[]> map = new HashMap<>();

    // collecting leakedParameters
    final Class<LeakingParametersData> jClass = LeakingParametersData.class;
    final ClassReader classReader = new ClassReader(jClass.getResourceAsStream("/" + jClass.getName().replace('.', '/') + ".class"));
    classReader.accept(new ClassVisitor(Opcodes.API_VERSION) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        final MethodNode node = new MethodNode(Opcodes.API_VERSION, access, name, desc, signature, exceptions);
        final Member method = new Member(classReader.getClassName(), name, desc);
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
      Member method = new Member(Type.getType(jClass).getInternalName(), jMethod.getName(), Type.getMethodDescriptor(jMethod));
      Annotation[][] annotations = jMethod.getParameterAnnotations();
      for (int i = 0; i < annotations.length; i++) {
        boolean isLeaking = false;
        Annotation[] parameterAnnotations = annotations[i];
        for (Annotation parameterAnnotation : parameterAnnotations) {
          if (parameterAnnotation.annotationType() == ExpectLeaking.class) {
            isLeaking = true;
          }
        }
        assertEquals(method + " #" + i, isLeaking, map.get(method)[i]);
      }
    }
  }

  private void checkAnnotations(String className) {
    PsiClass psiClass = myJavaPsiFacade.findClass(className, GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);

    for (PsiMethod method : psiClass.getMethods()) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (method.isConstructor() && parameters.length == 0) continue;

      // not-null parameters
      for (int i = 0; i < parameters.length; i++) {
        PsiAnnotation inferredAnnotation = myBytecodeAnalysisService.findInferredAnnotation(parameters[i], AnnotationUtil.NOT_NULL);
        boolean expectNotNull = AnnotationUtil.isAnnotated(parameters[i], ExpectNotNull.class.getName(), 0);
        assertNullity(getMethodDisplayName(method) + "/arg#" + i, expectNotNull, inferredAnnotation);
      }

      // not-null result
      boolean expectNotNull = AnnotationUtil.isAnnotated(method, ExpectNotNull.class.getName(), 0);
      PsiAnnotation actualAnnotation = myBytecodeAnalysisService.findInferredAnnotation(method, AnnotationUtil.NOT_NULL);
      assertNullity(getMethodDisplayName(method), expectNotNull, actualAnnotation);

      // contracts
      PsiAnnotation expectedContract = AnnotationUtil.findAnnotation(method, ExpectContract.class.getName());
      PsiAnnotation actualContract = myBytecodeAnalysisService.findInferredAnnotation(method, Contract.class.getName());

      String expectedText = getContractText(expectedContract);
      String inferredText = getContractText(actualContract);

      assertEquals(getMethodDisplayName(method) + ":" + expectedText + " <> " + inferredText,
                   expectedText, inferredText);
    }
  }

  private static String getContractText(PsiAnnotation expectedContract) {
    return expectedContract == null ? "null" :
           expectedContract.getText().replaceFirst("^@.+Contract\\(", "@Contract(")
              .replace(" = ", "=")
              .replace(", ", ",");
  }

  @NotNull
  private static String getMethodDisplayName(PsiMethod method) {
    return method.getContainingClass().getQualifiedName() + "." + method.getName();
  }

  private static void assertNullity(String message, boolean expectedNotNull, PsiAnnotation inferredAnnotation) {
    if(expectedNotNull && inferredAnnotation == null) {
      fail(message+": @NotNull expected, but not inferred");
    } else if(!expectedNotNull && inferredAnnotation != null) {
      fail(message+": @NotNull inferred, but not expected");
    }
  }

  private void checkCompoundIds(Class<?> javaClass) {
    String javaClassName = javaClass.getCanonicalName();
    PsiClass psiClass = myJavaPsiFacade.findClass(javaClassName, GlobalSearchScope.moduleWithLibrariesScope(myModule));
    assertNotNull(psiClass);

    for (java.lang.reflect.Method javaMethod : javaClass.getDeclaredMethods()) {
      if(javaMethod.isSynthetic()) continue; // skip lambda runtime representation
      Member method = new Member(Type.getType(javaClass).getInternalName(), javaMethod.getName(), Type.getMethodDescriptor(javaMethod));
      boolean noKey = javaMethod.getAnnotation(ExpectNoPsiKey.class) != null;
      PsiMethod[] methods = psiClass.findMethodsByName(javaMethod.getName(), false);
      assertEquals("Must be single method: " + javaMethod, 1, methods.length);
      PsiMethod psiMethod = methods[0];
      checkCompoundId(method, psiMethod, noKey);
    }

    for (Constructor<?> constructor : javaClass.getDeclaredConstructors()) {
      Member method = new Member(Type.getType(javaClass).getInternalName(), "<init>", Type.getConstructorDescriptor(constructor));
      boolean noKey = constructor.getAnnotation(ExpectNoPsiKey.class) != null;
      PsiMethod[] constructors = psiClass.getConstructors();
      PsiMethod psiMethod = constructors[0];
      checkCompoundId(method, psiMethod, noKey);
    }
  }

  private void checkCompoundId(Member method, PsiMethod psiMethod, boolean noKey) {
    /*
    System.out.println();
    System.out.println(method.internalClassName);
    System.out.println(method.methodName);
    System.out.println(method.methodDesc);
    */


    EKey psiKey = BytecodeAnalysisConverter.psiKey(psiMethod, Direction.Out);
    if (noKey) {
      assertNull(psiKey);
      return;
    }
    else {
      assertNotNull(psiKey);
    }
    EKey asmKey = new EKey(method, Direction.Out, true);
    Assert.assertEquals(asmKey, psiKey);
    Assert.assertEquals(asmKey.hashed(myMessageDigest), psiKey.hashed(myMessageDigest));
  }

  private void setUpDataClasses() throws Exception {
    File classesDir = new File(Test01.class.getResource("/" + PACKAGE_PATH).toURI());
    String basePath = myModule.getProject().getBasePath();
    File destDir = new File(basePath + "/classes/" + PACKAGE_PATH);
    FileUtil.copyDir(classesDir, destDir);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destDir);
    assertNotNull(vFile);
    PsiTestUtil.addLibrary(myModule, "dataClasses", vFile.getPath(), new String[]{""}, ArrayUtil.EMPTY_STRING_ARRAY);

    setUpPrecompiledDataClasses(basePath);
  }

  private void setUpPrecompiledDataClasses(String basePath) throws IOException {
    File sourcePath = new File(PlatformTestUtil.getCommunityPath() + "/java/java-tests/testSrc/" + PACKAGE_PATH);
    File[] sourceFiles = new File(sourcePath.getParentFile(), "precompiledData").listFiles((dir, name) -> name.endsWith(".java"));
    assertNotNull(sourceFiles);
    File conflictOutput = new File(basePath + "/precompiled/"+PACKAGE_PATH);
    assertTrue(conflictOutput.mkdirs());
    for (File file : sourceFiles) {
      File precompiledFile = new File(file.getParentFile(), file.getName().replaceFirst(".java$", ".class"));
      if(!precompiledFile.exists()) {
        fail("Unable to find precompiled "+precompiledFile+" for source file "+file);
      }
      FileUtil.copy(file, new File(conflictOutput, file.getName()));
      FileUtil.copy(precompiledFile, new File(conflictOutput, precompiledFile.getName()));
    }

    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(conflictOutput);
    assertNotNull(vFile);
    PsiTestUtil.addLibrary(myModule, "precompiled", vFile.getPath(), new String[]{""}, ArrayUtil.EMPTY_STRING_ARRAY);
  }
}