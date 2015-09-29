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

/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.extractclass.ExtractClassProcessor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

public class ExtractClassTest extends MultiFileTestCase{
  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/extractClass/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTestMethod() throws Exception {
    doTestMethod(null);
  }

  private void doTestMethod(String conflicts) throws Exception {
    doTestMethod("foo", conflicts);
  }

  private void doTestMethod(final String methodName, final String conflicts) throws Exception {
    doTestMethod(methodName, conflicts, "Test");
  }

  private void doTestMethod(final String methodName,
                            final String conflicts,
                            final String qualifiedName) throws Exception {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass(qualifiedName, GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiMethod> methods = new ArrayList<>();
      methods.add(aClass.findMethodsByName(methodName, false)[0]);

      doTest(aClass, methods, new ArrayList<>(), conflicts, false);
    });
  }

  public void testStatic() throws Exception {
    doTestMethod();
  }

  public void testStaticImport() throws Exception {
    doTestMethod();
  }

  public void testFieldReference() throws Exception {
    doTestMethod("foo", "Field 'myField' needs getter");
  }
  
  public void testIncrement() throws Exception {
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doTestField(null, false);
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  public void testVarargs() throws Exception {
    doTestMethod();
  }

  public void testNoDelegation() throws Exception {
    doTestMethod();
  }

  public void testNoFieldDelegation() throws Exception {
    doTestFieldAndMethod();
  }

  public void testFieldInitializers() throws Exception {
    doTestField(null);
  }

  public void testDependantFieldInitializers() throws Exception {
    doTestField(null);
  }

  public void testDependantNonStaticFieldInitializers() throws Exception {
    doTestField(null, true);
  }

  public void testInheritanceDelegation() throws Exception {
    doTestMethod();
  }

  public void testEnumSwitch() throws Exception {
    doTestMethod();
  }

  public void testImplicitReferenceTypeParameters() throws Exception {
    doTestMethod();
  }

  public void testStaticImports() throws Exception {
    doTestMethod("foo", null, "foo.Test");
  }

  public void testNoConstructorParams() throws Exception {
    doTestFieldAndMethod();
  }

  public void testConstructorParams() throws Exception {
    doTestFieldAndMethod();
  }

  private void doTestFieldAndMethod() throws Exception {
    doTestFieldAndMethod("bar");
  }

  public void testInnerClassRefs() throws Exception {
    doTestInnerClass();
  }

  private void doTestFieldAndMethod(final String methodName) throws Exception {
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

  private void doTestField(final String conflicts) throws Exception {
    doTestField(conflicts, false);
  }

  private void doTestField(final String conflicts, final boolean generateGettersSetters) throws Exception {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiMethod> methods = new ArrayList<>();

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      doTest(aClass, methods, fields, conflicts, generateGettersSetters);
    });
  }

  public void testInnerClass() throws Exception {
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
                                                                  "Extracted", null, generateGettersSetters, Collections.<MemberInfo>emptyList());
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

  public void testGenerateGetters() throws Exception {
    doTestField(null, true);
  }

  public void testIncrementDecrement() throws Exception {
    doTestField(null, true);
  }


  public void testGetters() throws Exception {
    doTestFieldAndMethod("getMyT");
  }

  public void testHierarchy() throws Exception {
    doTestFieldAndMethod();
  }

  public void testPublicFieldDelegation() throws Exception {
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

  private void doTestInnerClass() throws Exception {
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

  public void testInner() throws Exception {
    doTestInnerClass();
  }

  public void testMultipleGetters() throws Exception {
    doTestField("Field 'myT' needs getter");
  }

  public void testMultipleGetters1() throws Exception {
    doTestMethod("getMyT", "Field 'myT' needs getter");
  }

  public void testUsedInInitializer() throws Exception {
    doTestField("Field 'myT' needs setter\n" +
                "Field 'myT' needs getter\n" +
                "Class initializer requires moved members");
  }

  public void testUsedInConstructor() throws Exception {
    doTestField("Field 'myT' needs getter\n" +
                "Field 'myT' needs setter\n" +
                "Constructor requires moved members");
  }

  public void testRefInJavadoc() throws Exception {
    doTestField(null);
  }

  public void testMethodTypeParameters() throws Exception {
    doTestMethod();
  }

  public void testPublicVisibility() throws Exception {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));

      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiMethod> methods = new ArrayList<>();
      methods.add(aClass.findMethodsByName("foos", false)[0]);

      final ArrayList<PsiField> fields = new ArrayList<>();
      fields.add(aClass.findFieldByName("myT", false));

      final ExtractClassProcessor processor =
        new ExtractClassProcessor(aClass, fields, methods, new ArrayList<>(), "", null, "Extracted", PsiModifier.PUBLIC, false, Collections.<MemberInfo>emptyList());
      processor.run();
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }
}