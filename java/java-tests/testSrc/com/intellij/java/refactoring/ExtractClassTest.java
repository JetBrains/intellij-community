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

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.extractclass.ExtractClassProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ExtractClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/extractClass/");

  private void doTestMethod() {
    doTestMethod(null);
  }

  private void doTestMethod(String conflicts) {
    doTestMethod("foo", conflicts);
  }

  private void doTestMethod(final String methodName, final String conflicts) {
    doTestMethod(methodName, conflicts, "Test");
  }

  private void doTestMethod(final String methodName,
                            final String conflicts,
                            final String qualifiedName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass(qualifiedName);

      assertNotNull(aClass, "Class Test not found");

      final ArrayList<PsiMethod> methods = new ArrayList<>();
      methods.add(aClass.findMethodsByName(methodName, false)[0]);

      doTest(aClass, methods, new ArrayList<>(), conflicts, false);
    });
  }

  @Test
  public void testStatic() {
    doTestMethod();
  }

  @Test
  public void testStaticImport() {
    doTestMethod();
  }

  @Test
  public void testFieldReference() {
    doTestMethod("foo", "Field 'myField' needs getter");
  }
  
  @Test
  public void testIncrement() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doTestField(null, false));
  }

  @Test
  public void testVarargs() {
    doTestMethod();
  }

  @Test
  public void testNoDelegation() {
    doTestMethod();
  }

  @Test
  public void testNoFieldDelegation() {
    doTestFieldAndMethod();
  }

  @Test
  public void testFieldInitializers() {
    doTestField(null);
  }

  @Test
  public void testNonNormalizedFields() {
    doTestField(null);
  }

  @Test
  public void testDependantFieldInitializers() {
    doTestField(null);
  }

  @Test
  public void testDependantNonStaticFieldInitializers() {
    doTestField(null, true);
  }

  @Test
  public void testInheritanceDelegation() {
    doTestMethod();
  }

  @Test
  public void testEnumSwitch() {
    doTestMethod();
  }

  @Test
  public void testImplicitReferenceTypeParameters() {
    doTestMethod();
  }

  @Test
  public void testTypeParameters() {
    doTestMethod();
  }

  @Test
  public void testStaticImports() {
    doTestMethod("foo", null, "foo.Test");
  }

  @Test
  public void testNoConstructorParams() {
    doTestFieldAndMethod();
  }

  @Test
  public void testConstructorParams() {
    doTestFieldAndMethod();
  }

  private void doTestFieldAndMethod() {
    doTestFieldAndMethod("bar");
  }

  @Test
  public void testInnerClassRefs() {
    doTestInnerClass();
  }

  @Test
  public void testEnsurePreservedQualifier() {
    doTestMethod();
  }

  private void doTestFieldAndMethod(final String methodName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass("Test");

      assertNotNull(aClass, "Class Test not found");

      final ArrayList<PsiMethod> methods = new ArrayList<>();
      methods.add(aClass.findMethodsByName(methodName, false)[0]);

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      doTest(aClass, methods, fields, null, false);
    });
  }

  private void doTestField(final String conflicts) {
    doTestField(conflicts, false);
  }

  private void doTestField(final String conflicts, final boolean generateGettersSetters) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass("Test");

      assertNotNull(aClass, "Class Test not found");

      final ArrayList<PsiMethod> methods = new ArrayList<>();

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      doTest(aClass, methods, fields, conflicts, generateGettersSetters);
    });
  }

  @Test
  public void testInnerClass() {
    doTestExtractInnerFromField("Test", "myT");
  }

  @Test
  public void testInnerClass2() {
    // IDEA-350071
    doTestExtractInnerFromField("test", "mylist");
  }

  private void doTestExtractInnerFromField(String className, String fieldName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass(className);

      assertNotNull(aClass, "Class " + className + " not found");

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName(fieldName, false));

      doTest(aClass, new ArrayList<>(), fields, null, true, true);
    });
  }

  private static void doTest(final PsiClass aClass,
                             final ArrayList<PsiMethod> methods,
                             final ArrayList<PsiField> fields,
                             final String conflicts,
                             boolean generateGettersSetters) {
    doTest(aClass, methods, fields, conflicts, generateGettersSetters, false);
  }

  private static void doTest(final PsiClass aClass,
                             final ArrayList<PsiMethod> methods,
                             final ArrayList<PsiField> fields,
                             final String conflicts,
                             boolean generateGettersSetters,
                             boolean inner) {
    try {
      ExtractClassProcessor processor = new ExtractClassProcessor(aClass, fields, methods, new ArrayList<>(), StringUtil.getPackageName(aClass.getQualifiedName()), null,
                                                                  "Extracted", null, generateGettersSetters, Collections.emptyList(), inner);
      processor.run();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        TreeSet expectedConflictsSet = new TreeSet(Arrays.asList(conflicts.split("\n")));
        TreeSet actualConflictsSet = new TreeSet(Arrays.asList(e.getMessage().split("\n")));
        assertEquals(expectedConflictsSet, actualConflictsSet);
        return;
      } else {
        fail(e.getMessage());
      }
    }
    if (conflicts != null) {
      fail("Conflicts were not detected: " + conflicts);
    }
  }

  @Test
  public void testGenerateGetters() {
    doTestField(null, true);
  }

  @Test
  public void testIncrementDecrement() {
    doTestField(null, true);
  }

  @Test
  public void testGetters() {
    doTestFieldAndMethod("getMyT");
  }

  @Test
  public void testHierarchy() {
    doTestFieldAndMethod();
  }

  @Test
  public void testPublicFieldDelegation() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass("Test");

      assertNotNull(aClass, "Class Test not found");

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      ExtractClassProcessor processor = new ExtractClassProcessor(aClass, fields, new ArrayList<>(), new ArrayList<>(), "", "Extracted");
      processor.run();
    });
  }

  private void doTestInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass("Test");

      assertNotNull(aClass, "Class Test not found");

      final ArrayList<PsiClass> classes = new ArrayList<>();
      classes.add(aClass.findInnerClassByName("Inner", false));
      ExtractClassProcessor processor = new ExtractClassProcessor(aClass, new ArrayList<>(), new ArrayList<>(), classes, "", "Extracted");
      processor.run();
    });
  }

  @Test
  public void testInner() {
    doTestInnerClass();
  }

  @Test
  public void testMultipleGetters() {
    doTestField("Field 'myT' needs getter");
  }

  @Test
  public void testMultipleGetters1() {
    doTestMethod("getMyT", "Field 'myT' needs getter");
  }

  @Test
  public void testUsedInInitializer() {
    doTestField("""
                  Field 'myT' needs setter
                  Field 'myT' needs getter
                  Class initializer requires moved members""");
  }

  @Test
  public void testUsedInConstructor() {
    doTestField("""
                  Field 'myT' needs getter
                  Field 'myT' needs setter
                  Constructor requires moved members""");
  }

  @Test
  public void testRefInJavadoc() {
    doTestField(null);
  }

  @Test
  public void testMethodTypeParameters() {
    doTestMethod();
  }

  @Test
  public void testPublicVisibility() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      PsiClass aClass = myFixture.findClass("Test");

      assertNotNull(aClass, "Class Test not found");

      final ArrayList<PsiMethod> methods = new ArrayList<>();
      methods.add(aClass.findMethodsByName("foos", false)[0]);

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      final ExtractClassProcessor processor =
        new ExtractClassProcessor(aClass, fields, methods, new ArrayList<>(), "", null, "Extracted", PsiModifier.PUBLIC, false, Collections.emptyList(), false);
      processor.run();
    });
  }
}