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
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsDescriptor;
import com.intellij.refactoring.encapsulateFields.EncapsulateFieldsProcessor;
import com.intellij.refactoring.encapsulateFields.FieldDescriptor;
import com.intellij.refactoring.encapsulateFields.FieldDescriptorImpl;
import com.intellij.refactoring.encapsulateFields.JavaEncapsulateFieldHelper;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class EncapsulateFieldsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/refactoring/encapsulateFields/");
  }

  @Test
  public void testAlreadyExist() {
    doTest("i" , null);
  }

  @Test
  public void testDiffWithReturnTypeOnly() {
    doTest("i", "There is already method <b><code>Test setI(int)</code></b> which differs from setter <b><code>setI</code></b> by return type only");
  }

   @Test
   public void testDiffWithReturnTypeOnlyInHierarchy() {
     doTest("i", "There is already method <b><code>Super setI(int)</code></b> which differs from setter <b><code>setI</code></b> by return type only");
   }

  @Test
  public void testPostfixExpressionUsedInAssignment() {
    doTest("i", "Unable to proceed with postfix/prefix expression when it's result type is used");
  }

  @Test
  public void testHideOverriderMethod() {
    doTest("i", "A", "There is already a method <b><code>B.getI()</code></b> which would hide generated accessor for a.i");
  }

  @Test
  public void testJavadocRefs() {
    doTest("i", "A", null);
  }
  
  @Test
  public void testJavadocRefs1() {
    doTest("i", "B.A", null);
  }

  @Test
  public void testInaccessibleSuperMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         public class A {
                           int getFoo();
                         }""");
    doTest("foo", "b.B", null);
  }

  @Test
  public void testHideOuterclassMethod() {
    doTest("i", "A.B", "There is already a method <b><code>A.getI()</code></b> which would be hidden by generated accessor");
  }

  @Test
  public void testCommentsInside() {
    doTest("i", "A", null);
  }

  @Test
  public void testMoveJavadocToGetter() {
    doTest("i", "A", null);
  }

  @Test
  public void testFilterEnumConstants() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    PsiClass aClass = ReadAction.computeBlocking(() -> myFixture.findClass("A"));
    assertNotNull(aClass, "Tested class not found");
    PsiField[] fields = ReadAction.computeBlocking(() -> new JavaEncapsulateFieldHelper().getApplicableFields(aClass));
    doTest(aClass, null, true, true, fields);
    myFixture.checkResultByFile(myFixture.getTestName(false) +"_after.java");
  }

  private void doTest(final String fieldName, final String conflicts) {
    doTest(fieldName, "Test", conflicts);
  }

  private void doTest(final String fieldName, final String className, final String conflicts) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    PsiClass aClass = ReadAction.computeBlocking(() -> myFixture.findClass(className));
    assertNotNull(aClass, "Tested class not found");
    PsiField field = ReadAction.computeBlocking(() -> aClass.findFieldByName(fieldName, false));
    doTest(aClass, conflicts, true, true, field);
    myFixture.checkResultByFile(myFixture.getTestName(false) +"_after.java");
  }

  private static void doTest(final PsiClass aClass,
                             final String conflicts,
                             final boolean generateGetters,
                             final boolean generateSetters,
                             final PsiField... fields) {
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        final Project project = aClass.getProject();
        EncapsulateFieldsProcessor processor = new EncapsulateFieldsProcessor(project, new EncapsulateFieldsDescriptor() {
          @Override
          public FieldDescriptor[] getSelectedFields() {
            final FieldDescriptor[] descriptors = new FieldDescriptor[fields.length];
            for (int i = 0; i < fields.length; i++) {
              descriptors[i] = new FieldDescriptorImpl(
                fields[i],
                GenerateMembersUtil.suggestGetterName(fields[i]),
                GenerateMembersUtil.suggestSetterName(fields[i]),
                isToEncapsulateGet() ? GenerateMembersUtil.generateGetterPrototype(fields[i]) : null,
                isToEncapsulateSet() ? GenerateMembersUtil.generateSetterPrototype(fields[i]) : null
              );
            }
            return descriptors;
          }

          @Override
          public boolean isToEncapsulateGet() {
            return generateGetters;
          }

          @Override
          public boolean isToEncapsulateSet() {
            return generateSetters;
          }

          @Override
          public boolean isToUseAccessorsWhenAccessible() {
            return true;
          }

          @Override
          public String getFieldsVisibility() {
            return null;
          }

          @Override
          public String getAccessorsVisibility() {
            return PsiModifier.PUBLIC;
          }

          @Override
          public int getJavadocPolicy() {
            return DocCommentPolicy.MOVE;
          }

          @Override
          public PsiClass getTargetClass() {
            return aClass;
          }
        });
        processor.run();
        StandardFileSystems.local().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        if (conflicts != null) {
          assertEquals(conflicts, e.getMessage());
          return;
        }
        else {
          e.printStackTrace();
          fail(e.getMessage());
        }
      }
      if (conflicts != null) {
        fail("Conflicts were not detected: " + conflicts);
      }
    });
  }

}
