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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.extractclass.ExtractClassProcessor;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

public class ExtractClassTest extends MultiFileTestCase {
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/extractClass/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

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
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass(qualifiedName, GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiMethod> methods = new ArrayList<>();
      methods.add(aClass.findMethodsByName(methodName, false)[0]);

      doTest(aClass, methods, new ArrayList<>(), conflicts, false);
    });
  }

  public void testStatic() {
    doTestMethod();
  }

  public void testStaticImport() {
    doTestMethod();
  }

  public void testFieldReference() {
    doTestMethod("foo", "Field 'myField' needs getter");
  }
  
  public void testIncrement() {
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doTestField(null, false);
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  public void testVarargs() {
    doTestMethod();
  }

  public void testNoDelegation() {
    doTestMethod();
  }

  public void testNoFieldDelegation() {
    doTestFieldAndMethod();
  }

  public void testFieldInitializers() {
    doTestField(null);
  }

  public void testNonNormalizedFields() {
    doTestField(null);
  }

  public void testDependantFieldInitializers() {
    doTestField(null);
  }

  public void testDependantNonStaticFieldInitializers() {
    doTestField(null, true);
  }

  public void testInheritanceDelegation() {
    doTestMethod();
  }

  public void testEnumSwitch() {
    doTestMethod();
  }

  public void testImplicitReferenceTypeParameters() {
    doTestMethod();
  }

  public void testStaticImports() {
    doTestMethod("foo", null, "foo.Test");
  }

  public void testNoConstructorParams() {
    doTestFieldAndMethod();
  }

  public void testConstructorParams() {
    doTestFieldAndMethod();
  }

  private void doTestFieldAndMethod() {
    doTestFieldAndMethod("bar");
  }

  public void testInnerClassRefs() {
    doTestInnerClass();
  }

  public void testEnsurePreservedQualifier() {
    doTestMethod();
  }

  private void doTestFieldAndMethod(final String methodName) {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

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
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiMethod> methods = new ArrayList<>();

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      doTest(aClass, methods, fields, conflicts, generateGettersSetters);
    });
  }

  public void testInnerClass() {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

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
                                                                  "Extracted", null, generateGettersSetters, Collections.emptyList());
      processor.setExtractInnerClass(inner);
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        TreeSet expectedConflictsSet = new TreeSet(Arrays.asList(conflicts.split("\n")));
        TreeSet actualConflictsSet = new TreeSet(Arrays.asList(e.getMessage().split("\n")));
        Assert.assertEquals(expectedConflictsSet, actualConflictsSet);
        return;
      } else {
        fail(e.getMessage());
      }
    }
    if (conflicts != null) {
      fail("Conflicts were not detected: " + conflicts);
    }
  }

  public void testGenerateGetters() {
    doTestField(null, true);
  }

  public void testIncrementDecrement() {
    doTestField(null, true);
  }


  public void testGetters() {
    doTestFieldAndMethod("getMyT");
  }

  public void testHierarchy() {
    doTestFieldAndMethod();
  }

  public void testPublicFieldDelegation() {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      ExtractClassProcessor processor = new ExtractClassProcessor(aClass, fields, new ArrayList<>(), new ArrayList<>(), "", "Extracted");
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }

  private void doTestInnerClass() {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiClass> classes = new ArrayList<>();
      classes.add(aClass.findInnerClassByName("Inner", false));
      ExtractClassProcessor processor = new ExtractClassProcessor(aClass, new ArrayList<>(), new ArrayList<>(), classes, "", "Extracted");
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }

  public void testInner() {
    doTestInnerClass();
  }

  public void testMultipleGetters() {
    doTestField("Field 'myT' needs getter");
  }

  public void testMultipleGetters1() {
    doTestMethod("getMyT", "Field 'myT' needs getter");
  }

  public void testUsedInInitializer() {
    doTestField("Field 'myT' needs setter\n" +
                "Field 'myT' needs getter\n" +
                "Class initializer requires moved members");
  }

  public void testUsedInConstructor() {
    doTestField("Field 'myT' needs getter\n" +
                "Field 'myT' needs setter\n" +
                "Constructor requires moved members");
  }

  public void testRefInJavadoc() {
    doTestField(null);
  }

  public void testMethodTypeParameters() {
    doTestMethod();
  }

  public void testPublicVisibility() {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiMethod> methods = new ArrayList<>();
      methods.add(aClass.findMethodsByName("foos", false)[0]);

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      final ExtractClassProcessor processor =
        new ExtractClassProcessor(aClass, fields, methods, new ArrayList<>(), "", null, "Extracted", PsiModifier.PUBLIC, false, Collections.emptyList());
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }
}