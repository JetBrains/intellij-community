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
import com.intellij.psi.*;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.containers.MultiMap;

import java.util.Arrays;
import java.util.HashSet;

//pull first method from class a.A to class b.B
public class PullUpMultifileTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/pullUp/";
  }

  private void doTest(final String... conflicts) {
    final MultiMap<PsiElement, String> conflictsMap = new MultiMap<>();
    doTest(() -> {
      final PsiClass srcClass = myFixture.findClass("a.A");
      assertNotNull("Source class not found", srcClass);

      final PsiClass targetClass = myFixture.findClass("b.B");
      assertNotNull("Target class not found", targetClass);

      final PsiMethod[] methods = srcClass.getMethods();
      assertTrue("No methods found", methods.length > 0);
      final MemberInfo[] membersToMove = new MemberInfo[1];
      final MemberInfo memberInfo = new MemberInfo(methods[0]);
      memberInfo.setChecked(true);
      membersToMove[0] = memberInfo;

      final PsiDirectory targetDirectory = targetClass.getContainingFile().getContainingDirectory();
      final PsiPackage targetPackage = targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
      conflictsMap.putAllValues(
        PullUpConflictsUtil.checkConflicts(membersToMove, srcClass, targetClass, targetPackage, targetDirectory,
                                           psiMethod -> PullUpProcessor.checkedInterfacesContain(Arrays.asList(membersToMove), psiMethod)));

      new PullUpProcessor(srcClass, targetClass, membersToMove, new DocCommentPolicy<>(DocCommentPolicy.ASIS)).run();
    });

    if (conflicts.length != 0 && conflictsMap.isEmpty()) {
      fail("Conflict was not detected");
    }
    final HashSet<String> values = new HashSet<>(conflictsMap.values());
    final HashSet<String> expected = new HashSet<>(Arrays.asList(conflicts));

    assertEquals(expected.size(), values.size());
    for (String value : values) {
      if (!expected.contains(value)) {
        fail("Conflict: " + value + " is unexpectedly reported");
      }
    }
  }


  public void testInaccessible() {
    doTest("Method <b><code>A.foo()</code></b> is package-private and will not be accessible from method <b><code>method2Move()</code></b>.",
           "Method <b><code>method2Move()</code></b> uses method <b><code>A.foo()</code></b>, which is not moved to the superclass");
  }

  public void testAccessibleViaInheritanceInsideAnonymousClass() {
    doTest("Method <b><code>method2Move()</code></b> uses method <b><code>A.bar()</code></b>, which is not accessible from the superclass",
           "Method <b><code>A.bar()</code></b> is protected and will not be accessible from method <b><code>method2Move()</code></b>.");
  }

  public void testReuseSuperMethod() {
    doTest();
  }

   public void testReuseSuperSuperMethod() {
    doTest();
  }

  public void testClassPackageConflict() {
    doTest(() -> {
      final PsiClass srcClass = myFixture.findClass("a.a");
      assertNotNull("Source class not found", srcClass);

      final PsiClass targetClass = myFixture.findClass("a.B");
      assertNotNull("Target class not found", targetClass);

      final PsiMethod[] methods = srcClass.getMethods();
      assertTrue("No methods found", methods.length > 0);
      final MemberInfo[] membersToMove = new MemberInfo[1];
      final MemberInfo memberInfo = new MemberInfo(methods[0]);
      memberInfo.setChecked(true);
      membersToMove[0] = memberInfo;
      new PullUpProcessor(srcClass, targetClass, membersToMove, new DocCommentPolicy<>(DocCommentPolicy.ASIS)).run();
    });
  }
}
