/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.resolve

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

/**
 * @author peter
 */
class ResolveInLibrariesTest extends JavaCodeInsightFixtureTestCase {

  public void "test prefer current library when navigation from its source"() {
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib")
    def nanoJar = lib.children.find { it.name.startsWith("nanoxml") }
    def nanoSrc = lib.findChild("src").children.find { it.name.startsWith("nanoxml") }

    def jarCopy = myFixture.copyFileToProject(nanoJar.path, 'lib/nanoJar.jar')
    def srcCopy = myFixture.copyFileToProject(nanoSrc.path, 'lib/nanoSrc.zip')

    PsiTestUtil.addLibrary(myModule, 'nano1', lib.path, ["/$nanoJar.name!/"] as String[], ["/src/$nanoSrc.name!/"] as String[])
    PsiTestUtil.addLibrary(myModule, 'nano2', jarCopy.parent.path, ["/$jarCopy.name!/"] as String[], ["/$srcCopy.name!/"] as String[])

    def parsers = JavaPsiFacade.getInstance(project).findClasses('net.n3.nanoxml.IXMLParser', GlobalSearchScope.allScope(project))
    assert parsers.size() == 2

    def file0 = parsers[0].navigationElement.containingFile
    assert file0.virtualFile.path.startsWith(nanoSrc.path)
    assert file0.findReferenceAt(file0.text.indexOf('IXMLReader reader')).resolve().navigationElement.containingFile.virtualFile.path.startsWith(nanoSrc.path)

    def file1 = parsers[1].navigationElement.containingFile
    assert file1.virtualFile.path.startsWith(srcCopy.path)
    assert file1.findReferenceAt(file1.text.indexOf('IXMLReader reader')).resolve().navigationElement.containingFile.virtualFile.path.startsWith(srcCopy.path)

  }

  public void "test inheritance transitivity"() {
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib")
    def protoJar = lib.children.find { it.name.startsWith("protobuf") }

    def jarCopy = myFixture.copyFileToProject(protoJar.path, 'lib/protoJar.jar')

    PsiTestUtil.addLibrary(myModule, 'proto1', lib.path, ["/$protoJar.name!/"] as String[], [] as String[])
    PsiTestUtil.addLibrary(myModule, 'proto2', jarCopy.parent.path, ["/$jarCopy.name!/"] as String[], [] as String[])

    def scope = GlobalSearchScope.allScope(project)

    def bottoms = JavaPsiFacade.getInstance(project).findClasses('com.google.protobuf.AbstractMessage', scope)
    assert bottoms.size() == 2

    def middles = JavaPsiFacade.getInstance(project).findClasses('com.google.protobuf.AbstractMessageLite', scope)
    assert middles.size() == 2

    def intfs = JavaPsiFacade.getInstance(project).findClasses('com.google.protobuf.MessageLite', scope)
    assert intfs.size() == 2

    for (i in 0..1) {
      assert middles[i].isInheritor(intfs[i], true)
      assert bottoms[i].isInheritor(intfs[i], true)
      assert bottoms[i].isInheritor(middles[i], true)
    }

    for (deep in [false, true]) {
      for (i in 0..1) {
        assert !middles[i].isInheritor(intfs[1-i], deep)
        assert !bottoms[i].isInheritor(intfs[1-i], deep)
        assert !bottoms[i].isInheritor(middles[1-i], deep)
      }
    }
  }

}
