/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.memberPushDown.PushDownProcessor;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.containers.MultiMap;
import junit.framework.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

//pull first method from class a.A to class b.B
public class PullUpMultifileTest extends MultiFileTestCase {
  protected String getTestRoot() {
    return "/refactoring/pullUp/";
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }


  private void doTest(final String... conflicts) throws Exception {
    final MultiMap<PsiElement, String> conflictsMap = new MultiMap<PsiElement, String>();
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        final PsiClass srcClass = myJavaFacade.findClass("a.A", GlobalSearchScope.allScope(myProject));
        assertTrue("Source class not found", srcClass != null);

        final PsiClass targetClass = myJavaFacade.findClass("b.B", GlobalSearchScope.allScope(myProject));
        assertTrue("Target class not found", targetClass != null);

        final PsiMethod[] methods = srcClass.getMethods();
        assertTrue("No methods found", methods.length > 0);
        final MemberInfo[] membersToMove = new MemberInfo[1];
        final MemberInfo memberInfo = new MemberInfo(methods[0]);
        memberInfo.setChecked(true);
        membersToMove[0] = memberInfo;

        final PsiDirectory targetDirectory = targetClass.getContainingFile().getContainingDirectory();
        final PsiPackage targetPackage = targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
        conflictsMap.putAllValues(
          PullUpConflictsUtil.checkConflicts(membersToMove, srcClass, targetClass, targetPackage, targetDirectory, new InterfaceContainmentVerifier() {
            public boolean checkedInterfacesContain(PsiMethod psiMethod) {
              return PullUpHelper.checkedInterfacesContain(Arrays.asList(membersToMove), psiMethod);
            }
          }));

        new PullUpHelper(srcClass, targetClass, membersToMove, new DocCommentPolicy(DocCommentPolicy.ASIS)).run();
      }
    });

    if (conflicts.length != 0 && conflictsMap.isEmpty()) {
      fail("Conflict was not detected");
    }
    final HashSet<String> values = new HashSet<String>(conflictsMap.values());
    final HashSet<String> expected = new HashSet<String>(Arrays.asList(conflicts));

    assertEquals(expected.size(), values.size());
    for (String value : values) {
      if (!expected.contains(value)) {
        fail("Conflict: " + value + " is unexpectedly reported");
      }
    }
  }


  public void testInaccessible() throws Exception {
    doTest("Method <b><code>A.foo()</code></b> is package local and will not be accessible from method <b><code>method2Move()</code></b>.",
           "Method <b><code>method2Move()</code></b> uses method <b><code>A.foo()</code></b>, which is not moved to the superclass");
  }

  public void testReuseSuperMethod() throws Exception {
    doTest();
  }

   public void testReuseSuperSuperMethod() throws Exception {
    doTest();
  }
}
