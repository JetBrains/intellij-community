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
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
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
      assert ClassInheritorsSearch.search(intfs[i]).findAll().containsAll([middles[i], bottoms[i]])
      intfs[i].methods.each {
        assert OverridingMethodsSearch.search(it).findAll()
      }

      assert middles[i].isInheritor(intfs[i], true)
      assert bottoms[i].isInheritor(intfs[i], true)
      assert bottoms[i].isInheritor(middles[i], true)
    }

  }

  public void "test accept that with different library versions inheritance relation may be intransitive"() {
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/libResolve/inheritance")

    //Foo, Middle implements Foo, Other extends Middle
    PsiTestUtil.addLibrary(myModule, 'full', lib.path, ["/fullLibrary.jar!/"] as String[], [] as String[])

    //Middle, Bottom extends Middle
    PsiTestUtil.addLibrary(myModule, 'partial', lib.path, ["/middleBottom.jar!/"] as String[], [] as String[])

    def scope = GlobalSearchScope.allScope(project)

    def i0 = JavaPsiFacade.getInstance(project).findClass('Intf', scope)
    def other0 = JavaPsiFacade.getInstance(project).findClass('Other', scope)
    def b1 = JavaPsiFacade.getInstance(project).findClass('Bottom', scope)

    def middles = JavaPsiFacade.getInstance(project).findClasses('Middle', scope)
    assert middles.size() == 2
    def m0 = middles[0]
    def m1 = middles[1]

    for (deep in [false, true]) {
      assert m0.isInheritor(i0, deep)
      assert other0.isInheritor(m0, deep)

      assert !b1.isInheritor(i0, deep)

      assert b1.isInheritor(m0, deep)
      assert b1.isInheritor(m1, deep)

      assert !m1.isInheritor(i0, deep)
    }

    assert other0.isInheritor(i0, true)
    assert !other0.isInheritor(i0, false)

    assert ClassInheritorsSearch.search(i0).findAll() == [m0, b1, other0]
    assert ClassInheritorsSearch.search(m0).findAll() == [b1, other0]

    assert fooInheritors(i0) == [fooMethod(m0), fooMethod(other0)] as Set
    assert fooInheritors(m0) == [fooMethod(other0), fooMethod(b1)] as Set
    assert fooInheritors(m1) == [fooMethod(other0), fooMethod(b1)] as Set
  }

  private static PsiMethod fooMethod(PsiClass c) { c.findMethodsByName('foo', false)[0] }
  private static Set<PsiMethod> fooInheritors(PsiClass c) { OverridingMethodsSearch.search(fooMethod(c)).findAll() as Set }

  public void "test do not parse not stubbed sources in class jars"() {
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/libResolve/classesAndSources")
    PsiTestUtil.addLibrary(myModule, 'cas', lib.path, ["/classesAndSources.jar!/"] as String[], ["/classesAndSources.jar!/"] as String[])

    def facade = JavaPsiFacade.getInstance(project)
    def scope = GlobalSearchScope.allScope(project)

    assert facade.findClasses('LibraryClass', scope).size() == 1

    def pkg = facade.findPackage("")
    assert pkg.classes.size() == 1

    Collection<VirtualFile> pkgDirs = pkg.directories.collect { it.virtualFile }
    Collection<VirtualFile> pkgChildren = pkgDirs.collect { it.children as List }.flatten()
    PsiFile javaSrc = psiManager.findFile(pkgChildren.find { it.name == 'LibraryClass.java' })
    assert !javaSrc.contentsLoaded
    assert !javaSrc.stub

    assert pkg.containsClassNamed('LibraryClass')
    assert !javaSrc.contentsLoaded
    assert !javaSrc.stub
    assert !javaSrc.node.parsed
  }

  @Override
  protected boolean toAddSourceRoot() {
    return false;
  }

  public void "test do not build stubs in source jars"() {
    def facade = JavaPsiFacade.getInstance(project)
    def scope = GlobalSearchScope.allScope(project)

    String testDataPathForTest = PathManagerEx.getTestDataPath() + "/libResolve/classesAndSources"
    def lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(testDataPathForTest)
    def localFile = myFixture.copyFileToProject(testDataPathForTest + File.separator + "Foo.java", 'Foo.java')
    assert localFile != null

    checkFileIsNotLoadedAndHasNoStub(localFile)
    assert facade.findClasses('Foo', scope).size() == 0
    PsiTestUtil.addLibrary(myModule, 'cas', lib.path, [] as String[], ["/classesAndSources.jar!/"] as String[])

    def vfile = lib.findChild("classesAndSources.jar")
    assert vfile != null
    vfile = JarFileSystem.getInstance().getJarRootForLocalFile(vfile);
    assert vfile != null
    vfile = vfile.findChild('LibraryClass.java');
    assert vfile != null

    assert facade.findClasses('LibraryClass', scope).size() == 0

    checkFileIsNotLoadedAndHasNoStub(vfile)
  }

  private void checkFileIsNotLoadedAndHasNoStub(VirtualFile vfile) {
    def file = PsiManager.getInstance(project).findFile(vfile);
    assert file != null

    assert !file.contentsLoaded
    assert !file.stub
  }
}
