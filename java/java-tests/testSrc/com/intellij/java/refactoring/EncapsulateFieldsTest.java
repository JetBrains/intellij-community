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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.encapsulateFields.*;
import com.intellij.refactoring.util.DocCommentPolicy;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

public class EncapsulateFieldsTest extends MultiFileTestCase {
  public void testAlreadyExist() {
    doTest("i" , null);
  }

  public void testDiffWithReturnTypeOnly() {
    doTest("i", "There is already method <b><code>Test setI(int)</code></b> which differs from setter <b><code>setI</code></b> by return type only");
  }

   public void testDiffWithReturnTypeOnlyInHierarchy() {
    doTest("i", "There is already method <b><code>Super setI(int)</code></b> which differs from setter <b><code>setI</code></b> by return type only");
  }

  public void testPostfixExpressionUsedInAssignment() {
    doTest("i", "Unable to proceed with postfix/prefix expression when it's result type is used");
  }

  public void testHideOverriderMethod() {
    doTest("i", "A", "There is already a method <b><code>B.getI()</code></b> which would hide generated getter for a.i");
  }

  public void testJavadocRefs() {
    doTest("i", "A", null);
  }
  
  public void testJavadocRefs1() {
    doTest("i", "B.A", null);
  }

  public void testHideOuterclassMethod() {
    doTest("i", "A.B", "There is already a method <b><code>A.getI()</code></b> which would be hidden by generated getter");
  }

  public void testCommentsInside() {
    doTest("i", "A", null);
  }

  public void testMoveJavadocToGetter() {
    doTest((rootDir, rootAfter) -> {
      final PsiClass aClass = myJavaFacade.findClass("A", GlobalSearchScope.projectScope(myProject));
      assertNotNull("Tested class not found", aClass);
      final PsiField field = aClass.findFieldByName("i", false);
      assertNotNull(field);
      doTest(aClass, null, true, true, field);
    });
  }

  public void testFilterEnumConstants() {
    doTest((rootDir, rootAfter) -> {
      final PsiClass aClass = myJavaFacade.findClass("A", GlobalSearchScope.projectScope(myProject));
      assertNotNull("Tested class not found", aClass);
      doTest(aClass, null, true, true, new JavaEncapsulateFieldHelper().getApplicableFields(aClass));
    });
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/encapsulateFields/";
  }


  private void doTest(final String fieldName, final String conflicts) {
    doTest(fieldName, "Test", conflicts);
  }

  private void doTest(final String fieldName, final String className, final String conflicts) {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.projectScope(myProject));

      assertNotNull("Tested class not found", aClass);


      doTest(aClass, conflicts, true, true, aClass.findFieldByName(fieldName, false));
    });
  }


  private static void doTest(final PsiClass aClass,
                             final String conflicts,
                             final boolean generateGetters,
                             final boolean generateSetters,
                             final PsiField... fields) {
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
      LocalFileSystem.getInstance().refresh(false);
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (conflicts != null) {
        Assert.assertEquals(conflicts, e.getMessage());
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
  }

}
