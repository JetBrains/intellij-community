// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter;
import com.intellij.codeInspection.bytecodeAnalysis.Direction;
import com.intellij.codeInspection.bytecodeAnalysis.EKey;
import com.intellij.codeInspection.bytecodeAnalysis.Member;
import com.intellij.codeInspection.bytecodeAnalysis.ProjectBytecodeAnalysis;
import com.intellij.codeInspection.bytecodeAnalysis.asm.LeakingParameters;
import com.intellij.lang.jvm.JvmAnnotation;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class BytecodeAnalysisTest {
  private static final String PACKAGE_NAME = "bytecodeAnalysis";
  private static final String EXPECT_NOT_NULL = PACKAGE_NAME + ".ExpectNotNull";
  private static final String EXPECT_CONTRACT = PACKAGE_NAME + ".ExpectContract";
  private static final String EXPECT_LEAKING = PACKAGE_NAME + ".ExpectLeaking";
  private static final String EXPECT_NO_PSI_KEY = PACKAGE_NAME + ".ExpectNoPsiKey";

  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      String dataDir = JavaTestUtil.getJavaTestDataPath() + "/codeInspection/bytecodeAnalysis/data";
      PsiTestUtil.newLibrary("velocity").classesRoot(dataDir + "/classes").classesRoot(dataDir + "/conflict").addTo(model);
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testInference() {
    checkAnnotations("data.Test01");
    checkAnnotations("data.Test02");
    checkAnnotations("data.TestNonStable");
    checkAnnotations("data.TestConflict");
    checkAnnotations("data.TestEnum");
    checkAnnotations("data.TestField");
  }

  @Test
  public void testJava9Inference() {
    checkAnnotations("java9.TestStringConcat");
  }

  @Test
  public void testHashCollision() {
    checkAnnotations("data.TestHashCollision");
  }

  @Test
  public void testConverter() throws IOException {
    checkCompoundIds("data.Test01");
    checkCompoundIds("data.TestConverterData");
    checkCompoundIds("data.TestConverterData.StaticNestedClass");
    checkCompoundIds("data.TestConverterData.InnerClass");
    checkCompoundIds("data.TestConverterData.GenericStaticNestedClass");
    checkCompoundIds("data.TestAnnotation");
  }

  @Test
  public void testLeakingParametersAnalysis() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Map<String, boolean[]> map = new HashMap<>();

    GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(myFixture.getModule());
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass(PACKAGE_NAME + ".data.TestLeakingParametersData", scope);
      assertNotNull(psiClass);
      try (InputStream stream = getVirtualFile(psiClass).getInputStream()) {
        ClassReader reader = new ClassReader(stream);
        reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
          @Override
          public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodNode node = new MethodNode(Opcodes.API_VERSION, access, name, desc, signature, exceptions);
            return new MethodVisitor(Opcodes.API_VERSION, node) {
              @Override
              public void visitEnd() {
                try {
                  map.put(name, LeakingParameters.build(reader.getClassName(), node, false).parameters);
                }
                catch (AnalyzerException ignore) { }
              }
            };
          }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      }

      for (PsiMethod method : psiClass.getMethods()) {
        JvmParameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
          boolean isLeaking = false;
          for (JvmAnnotation annotation : parameters[i].getAnnotations()) {
            if (EXPECT_LEAKING.equals(annotation.getQualifiedName())) {
              isLeaking = true;
            }
          }
          assertEquals(isLeaking, map.get(method.getName())[i], method + " #" + i);
        }
      }
    });
  }

  private void checkAnnotations(String className) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(myFixture.getModule());
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass(PACKAGE_NAME + '.' + className, scope);
      assertNotNull(psiClass);
      ProjectBytecodeAnalysis service = ProjectBytecodeAnalysis.getInstance(myFixture.getProject());

      for (PsiMethod method : psiClass.getMethods()) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (method.isConstructor() && parameters.length == 0) continue;

        for (int i = 0; i < parameters.length; i++) {
          boolean expectNotNull = AnnotationUtil.isAnnotated(parameters[i], EXPECT_NOT_NULL, 0);
          PsiAnnotation inferredAnnotation = service.findInferredAnnotation(parameters[i], AnnotationUtil.NOT_NULL);
          assertNullity(displayName(method) + "/arg#" + i, expectNotNull, inferredAnnotation);
        }

        boolean expectNotNull = AnnotationUtil.isAnnotated(method, EXPECT_NOT_NULL, 0);
        PsiAnnotation actualAnnotation = service.findInferredAnnotation(method, AnnotationUtil.NOT_NULL);
        assertNullity(displayName(method), expectNotNull, actualAnnotation);

        PsiAnnotation expectedContract = AnnotationUtil.findAnnotation(method, EXPECT_CONTRACT);
        PsiAnnotation actualContract = service.findInferredAnnotation(method, Contract.class.getName());
        String expectedText = contractText(expectedContract);
        String inferredText = contractText(actualContract);
        assertEquals(expectedText, inferredText, displayName(method) + ":" + expectedText + " <> " + inferredText);
      }
      for (PsiField field : psiClass.getFields()) {
        boolean expectNotNull = AnnotationUtil.isAnnotated(field, EXPECT_NOT_NULL, 0);
        PsiAnnotation actualAnnotation = service.findInferredAnnotation(field, AnnotationUtil.NOT_NULL);
        assertNullity(displayName(field), expectNotNull, actualAnnotation);
      }
    });
  }

  private static String displayName(PsiMember member) {
    return member.getContainingClass().getQualifiedName() + "." + member.getName();
  }

  private static String contractText(PsiAnnotation contract) {
    return contract == null ? "null" : contract.getText().replaceFirst("^@.+Contract\\(", "@Contract(").replace(" = ", "=").replace(", ", ",");
  }

  private static void assertNullity(String message, boolean expectedNotNull, PsiAnnotation inferredAnnotation) {
    if (expectedNotNull && inferredAnnotation == null) {
      fail(message + ": @NotNull expected, but not inferred");
    }
    else if (!expectedNotNull && inferredAnnotation != null) {
      fail(message + ": @NotNull inferred, but not expected");
    }
  }
  
  private void checkCompoundIds(String className) throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GlobalSearchScope scope = GlobalSearchScope.moduleWithLibrariesScope(myFixture.getModule());
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = JavaPsiFacade.getInstance(myFixture.getProject()).findClass(PACKAGE_NAME + '.' + className, scope);
      assertNotNull(psiClass);

      try (InputStream stream = getVirtualFile(psiClass).getInputStream()) {
        ClassReader reader = new ClassReader(stream);
        reader.accept(new ClassVisitor(Opcodes.API_VERSION) {
          @Override
          public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
              Member method = new Member(reader.getClassName(), name, desc);
              PsiMethod[] psiMethods = "<init>".equals(name) ? psiClass.getConstructors() : psiClass.findMethodsByName(name, false);
              assertEquals(1, psiMethods.length, "Must be single method: " + name);
              PsiMethod psiMethod = psiMethods[0];
              boolean noKey = psiMethod.hasAnnotation(EXPECT_NO_PSI_KEY);
              checkCompoundId(method, psiMethod, noKey);
            }
            return null;
          }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      }
    });
  }

  private static void checkCompoundId(Member method, PsiMethod psiMethod, boolean noKey) {
    EKey psiKey = BytecodeAnalysisConverter.psiKey(psiMethod, Direction.Out);
    if (noKey) {
      assertNull(psiKey);
    }
    else {
      assertNotNull(psiKey);
      EKey asmKey = new EKey(method, Direction.Out, true);
      assertEquals(asmKey, psiKey);
      assertEquals(asmKey.hashed(), psiKey.hashed());
    }
  }

  private static VirtualFile getVirtualFile(PsiClass psiClass) {
    VirtualFile file = psiClass.getContainingFile().getVirtualFile();
    if (psiClass.getParent() instanceof PsiClass) {
      VirtualFile root = ProjectFileIndex.getInstance(psiClass.getProject()).getClassRootForFile(file);
      file = root.findFileByRelativePath(ClassUtil.getJVMClassName(psiClass).replace('.', '/') + ".class");
    }
    return file;
  }
}