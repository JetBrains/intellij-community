// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService

class RenamePackageTest extends JavaCodeInsightFixtureTestCase {
  void "test rename in package prefix"() {
    VirtualFile a = myFixture.addFileToProject("srcPrefix/foo/A.java", "package p.foo; class A extends B {}").virtualFile
    VirtualFile b = myFixture.addFileToProject("src/p/foo/B.java", "package p.foo; class B { }").virtualFile
    VirtualFile c = myFixture.addFileToProject("src/bar/C.java", "package bar; class C { p.foo.A a; p.foo.B b; }").virtualFile

    PsiTestUtil.removeSourceRoot(module, ModuleRootManager.getInstance(module).sourceRoots[0])
    PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.getFile('src'))
    PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.getFile('srcPrefix'), JavaSourceRootType.SOURCE,
                              JpsJavaExtensionService.instance.createSourceRootProperties("p"))

    myFixture.renameElement(myFixture.findPackage('p'), 'p1')
    FileDocumentManager.instance.saveAllDocuments()

    assert a.path.endsWith('srcPrefix/foo/A.java')
    assert b.path.endsWith('src/p1/foo/B.java')
    assert c.path.endsWith('src/bar/C.java')

    assert VfsUtilCore.loadText(a) == "package p1.foo; class A extends B {}"
    assert VfsUtilCore.loadText(b) == "package p1.foo; class B { }"
    assert VfsUtilCore.loadText(c) == "package bar; class C { p1.foo.A a; p1.foo.B b; }"

    assert myFixture.javaFacade.findPackage('p') == null
    assert myFixture.javaFacade.findPackage('p1').directories.size() == 2
  }

  void "test rename in resources"() {
    myFixture.addFileToProject("src/p/p/m.txt", "").virtualFile
    VirtualFile c = myFixture.addFileToProject("src/bar/C.java", """package bar; 
class C { 
  {
     C.class.getResource("/p/p/m.txt");
  } 
}""").virtualFile

    PsiTestUtil.removeSourceRoot(module, ModuleRootManager.getInstance(module).sourceRoots[0])
    PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.getFile('src'))

    myFixture.renameElement(myFixture.findPackage('p.p'), 'p.p1')
    FileDocumentManager.instance.saveAllDocuments()

    assert VfsUtilCore.loadText(c) == """package bar; 
class C { 
  {
     C.class.getResource("/p/p1/m.txt");
  } 
}"""
  }
}
