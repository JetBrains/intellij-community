// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.extractclass.ExtractClassProcessor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

public class ExtractEnumTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/extractEnum/";
  }
  
  public void testOneConstant() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true));
  }

  public void testDependantConstants() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }
  
  public void testConstructorCall() {
    doTest(new RefactoringTestUtil.MemberDescriptor("STATE_STARTED", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("STATE_STOPPED", PsiField.class, true));
  }
 
  public void testCondition() {
    doTest(new RefactoringTestUtil.MemberDescriptor("STATE_STARTED", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("STATE_STOPPED", PsiField.class, true));
  }

  public void testReferencesOnEnumConstantInEnum() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("foo", PsiMethod.class));
  }

  public void testReferencesOnEnumConstantInOriginal() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true));
  }

  public void testUsageInVariableInitializer() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true));
  }

  public void testNestedClass() {
    doTest(null, true,
           new RefactoringTestUtil.MemberDescriptor("ONE", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("TWO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("THREE", PsiField.class, true));
  }

  public void testForwardReferenceConflict() {
    doTest("Unable to migrate statement to enum constant.", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, false),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testValueNameConflict() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("value", PsiField.class, false));
  }

  public void testChangeMethodParameter() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testCantChangeMethodParameter() {
    doTest("Unable to migrate statement to enum constant.", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testDontChangeOtherConstants() {
    doTest("Unable to migrate statement to enum constant. Node.WARNING can not be replaced with enum", false,
           new RefactoringTestUtil.MemberDescriptor("OK", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("ERROR", PsiField.class, true));
  }

  public void testCantChangeMethodParameter1() {
    doTest("Unable to migrate statement to enum constant.", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testChangeReturnType() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testCantChangeReturnType() {
    doTest("Unable to migrate statement to enum constant. Field <b><code>length</code></b> is out of project", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testCantChangeReturnType1() {
    doTest("Unable to migrate statement to enum constant.", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testChangeMethodParameterAndReplaceOtherUsages() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testReferencesOnEnumConstantElsewhere() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testNormalize() {
    doTest(new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  public void testUnknownSwitchLabel() {
    doTest("Unable to migrate statement to enum constant. 8 can not be replaced with enum", false,
           new RefactoringTestUtil.MemberDescriptor("FOO", PsiField.class, true),
           new RefactoringTestUtil.MemberDescriptor("BAR", PsiField.class, true));
  }

  private void doTest(RefactoringTestUtil.MemberDescriptor... memberDescriptors) {
    doTest(null, false, memberDescriptors);
  }

  private void doTest(String conflicts,
                      boolean extractInnerClass,
                      RefactoringTestUtil.MemberDescriptor... memberDescriptors) {
    doTest(() -> {
      final PsiClass aClass = myFixture.findClass("Test");
      assertNotNull("Class Test not found", aClass);

      final ArrayList<PsiField> fields = new ArrayList<>();
      final ArrayList<PsiMethod> methods = new ArrayList<>();
      final List<MemberInfo> enumConstants = new ArrayList<>();
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
          new ExtractClassProcessor(aClass, fields, methods, new ArrayList<>(), "", null, "EEnum",
                                    null, false, enumConstants, extractInnerClass);

        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        if (conflicts != null) {
          TreeSet<String> expectedConflictsSet = new TreeSet<>(Arrays.asList(conflicts.split("\n")));
          TreeSet<String> actualConflictsSet = new TreeSet<>(Arrays.asList(e.getMessage().split("\n")));
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
    });
  }
}

