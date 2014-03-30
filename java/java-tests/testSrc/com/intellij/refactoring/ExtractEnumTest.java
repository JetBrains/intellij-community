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
 * Date: 02-Jun-2010
 */
package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.extractclass.ExtractClassProcessor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

public class ExtractEnumTest extends MultiFileTestCase {

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/extractEnum/";
  }

  public void testOneConstant() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true));
  }

  public void testDependantConstants() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testReferencesOnEnumConstantInEnum() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testReferencesOnEnumConstantInOriginal() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true));
  }

  public void testUsageInVariableInitializer() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true));
  }

  public void testForwardReferenceConflict() throws Exception {
    doTest("Unable to migrate statement to enum constant.", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, false),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testValueNameConflict() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("value", PsiField.class, false));
  }

  public void testChangeMethodParameter() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testCantChangeMethodParameter() throws Exception {
    doTest("Unable to migrate statement to enum constant.", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testDontChangeOtherConstants() throws Exception {
    doTest("Unable to migrate statement to enum constant. Node.WARNING can not be replaced with enum", false,
           new RefactoringTestUtil.MemberDescriptor("OK", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("ERROR", PsiField.class, true));
  }

  public void testCantChangeMethodParameter1() throws Exception {
    doTest("Unable to migrate statement to enum constant.", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testChangeReturnType() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testCantChangeReturnType() throws Exception {
    doTest("Unable to migrate statement to enum constant. Field &lt;b&gt;&lt;code&gt;length&lt;/code&gt;&lt;/b&gt; is out of project", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testCantChangeReturnType1() throws Exception {
    doTest("Unable to migrate statement to enum constant.", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testChangeMethodParameterAndReplaceOtherUsages() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testReferencesOnEnumConstantElsewhere() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testNormalize() throws Exception {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testUnknownSwitchLabel() throws Exception {
    doTest("Unable to migrate statement to enum constant. 8 can not be replaced with enum", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  private void doTest(final RefactoringTestUtil.MemberDescriptor... memberDescriptors) throws Exception {
    doTest(null, false, memberDescriptors);
  }

  private void doTest(final String conflicts,
                      final boolean generateAccessors,
                      final RefactoringTestUtil.MemberDescriptor... memberDescriptors) throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception {
        final PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(myProject));
        assertNotNull("Class Test not found", aClass);

        final ArrayList<PsiField> fields = new ArrayList<PsiField>();
        final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();
        final List<MemberInfo> enumConstants = new ArrayList<MemberInfo>();
        for (MemberInfo memberInfo : RefactoringTestUtil.findMembers(aClass, memberDescriptors)) {
          final PsiMember member = memberInfo.getMember();
          if (member instanceof PsiField) {
            fields.add((PsiField)member);
            if (member.hasModifierProperty(PsiModifier.STATIC) && member.hasModifierProperty(PsiModifier.FINAL) && ((PsiField)member).hasInitializer()) {
              if (memberInfo.isToAbstract()) {
                enumConstants.add(memberInfo);
                memberInfo.setChecked(true);
              }
            }
          }
          else if (member instanceof PsiMethod) {
            methods.add((PsiMethod)member);
          }
        }
        try {
          final ExtractClassProcessor processor =
            new ExtractClassProcessor(aClass, fields, methods, new ArrayList<PsiClass>(), "", null, "EEnum",
                                      null, generateAccessors, enumConstants);

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
          }
          else {
            fail(e.getMessage());
          }
        }
        if (conflicts != null) {
          fail("Conflicts were not detected: " + conflicts);
        }
      }
    });
  }
}

