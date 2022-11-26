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
package com.intellij.java.refactoring


import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

@CompileStatic
class RenameDirectoryTest extends JavaCodeInsightFixtureTestCase {

  void testRenameSrcRootWithTextOccurrences() {
    VirtualFile srcRoot = myFixture.tempDirFixture.findOrCreateDir("src2")
    PsiTestUtil.removeAllRoots(module, null)
    PsiTestUtil.addSourceRoot(module, srcRoot)

    def fooClass = myFixture.addClass("""
// PsiPackage:
class Foo {
  String s1 = "PsiPackage:"
}
""")
    myFixture.configureFromExistingVirtualFile(fooClass.containingFile.virtualFile)

    new RenameProcessor(getProject(), psiManager.findDirectory(srcRoot), "newName", true, true).run()

    assert srcRoot.path.endsWith("newName")
    myFixture.checkResult """
// PsiPackage:
class Foo {
  String s1 = "PsiPackage:"
}
"""
  }
}
