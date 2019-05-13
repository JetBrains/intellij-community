/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.gist

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.FileContentUtilCore
import com.intellij.util.ref.GCUtil
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.EnumeratorStringDescriptor
import groovy.transform.CompileStatic

/**
 * @author peter
 */
@CompileStatic
class FileGistTest extends LightCodeInsightFixtureTestCase {

  void "test get data"() {
    def gist = take3Gist()
    assert 'foo' == gist.getFileData(project, addFooBarFile())
    assert 'bar' == gist.getFileData(project, myFixture.addFileToProject('b.txt', 'bar foo').virtualFile)
  }

  private VirtualFile addFooBarFile() {
    return myFixture.addFileToProject('a.txt', 'foo bar').virtualFile
  }

  private VirtualFileGist<String> take3Gist() {
    return GistManager.instance.newVirtualFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, { p, f -> LoadTextUtil.loadText(f).toString().substring(0, 3) })
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ((GistManagerImpl)GistManager.instance).resetReindexCount()
    }
    finally {
      super.tearDown()
    }
  }

  void "test data is cached per file"() {
    VirtualFileGist<Integer> gist = countingVfsGist()

    def file = addFooBarFile()
    assert gist.getFileData(project, file) == 1
    assert gist.getFileData(project, file) == 1

    assert gist.getFileData(project, myFixture.addFileToProject('b.txt', '').virtualFile) == 2
  }

  private VirtualFileGist<Integer> countingVfsGist() {
    return GistManager.instance.newVirtualFileGist(getTestName(true), 0, EnumeratorIntegerDescriptor.INSTANCE, countingCalculator())
  }

  void "test data is recalculated on file change"() {
    VirtualFileGist<Integer> gist = countingVfsGist()
    def file = addFooBarFile()
    assert gist.getFileData(project, file) == 1

    WriteAction.run { VfsUtil.saveText(file, 'x') }
    assert gist.getFileData(project, file) == 2

    FileContentUtilCore.reparseFiles(file)
    assert gist.getFileData(project, file) == 3

    PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(file, Conditions.alwaysTrue())
    assert gist.getFileData(project, file) == 4
  }

  void "test data is not recalculated on another file change"() {
    VirtualFileGist<Integer> gist = countingVfsGist()
    def file1 = addFooBarFile()
    def file2 = myFixture.addFileToProject('b.txt', 'foo bar').virtualFile
    assert gist.getFileData(project, file1) == 1
    assert gist.getFileData(project, file2) == 2

    WriteAction.run { VfsUtil.saveText(file1, 'x') }
    assert gist.getFileData(project, file2) == 2
  }

  void "test vfs gist works for light files"() {
    assert 'goo' == take3Gist().getFileData(project, new LightVirtualFile('a.txt', 'goo goo'))
  }

  void "test different data for different projects"() {
    int invocations = 0
    VirtualFileGist<String> gist = GistManager.instance.newVirtualFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, { p, f -> "${p?.name} ${++invocations}" as String })
    def file = addFooBarFile()

    assert "$project.name 1" == gist.getFileData(project, file)
    assert "$ProjectManager.instance.defaultProject.name 2" == gist.getFileData(ProjectManager.instance.defaultProject, file)
    assert "$project.name 1" == gist.getFileData(project, file)
    assert "null 3" == gist.getFileData(null, file)
  }

  void "test cannot register twice"() {
    take3Gist()
    try {
      take3Gist()
      fail()
    }
    catch (IllegalArgumentException ignore) {
    }
  }

  private static VirtualFileGist.GistCalculator<Integer> countingCalculator() {
    int invocations = 0
    return { p, f -> ++invocations } as VirtualFileGist.GistCalculator<Integer>
  }

  void "test null data"() {
    int invocations = 0
    VirtualFileGist<String> gist = GistManager.instance.newVirtualFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, { p, f -> invocations++; return null as String })
    def file = addFooBarFile()
    assert null == gist.getFileData(project, file)
    assert null == gist.getFileData(project, file)
    assert invocations == 1
  }

  void "test psi gist uses last committed document content"() {
    def file = myFixture.addFileToProject("a.txt", "foo bar")
    def gist = GistManager.instance.newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, { it.text.substring(0, 3) })
    assert 'foo' == gist.getFileData(file)

    WriteAction.run {
      VfsUtil.saveText(file.virtualFile, 'bar foo')
      assert file.valid
      assert PsiDocumentManager.getInstance(project).isUncommited(file.viewProvider.document)
      assert 'foo' == gist.getFileData(file)
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    assert 'bar' == gist.getFileData(file)
  }


  void "test psi gist does not load AST"() {
    PsiFileImpl file = myFixture.addFileToProject("a.java", "package bar;") as PsiFileImpl
    assert !file.contentsLoaded

    assert GistManager.instance.newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, { it.findElementAt(0).text }).getFileData(file) == 'package'
    assert !file.contentsLoaded
  }

  void "test psi gist works for binary files"() {
    def objectClass = JavaPsiFacade.getInstance(project).findClass(Object.name, GlobalSearchScope.allScope(project)).containingFile

    assert GistManager.instance.newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, { it.name }).getFileData(objectClass) == 'Object.class'
  }

  void "test psi gist does not load document"() {
    PsiFileGist<Integer> gist = countingPsiGist()
    def file = myFixture.addFileToProject('a.xtt', 'foo')
    assert gist.getFileData(file) == 1

    GCUtil.tryGcSoftlyReachableObjects()
    assert !PsiDocumentManager.getInstance(project).getCachedDocument(file)

    assert gist.getFileData(file) == 1
    assert !PsiDocumentManager.getInstance(project).getCachedDocument(file)
  }

  private PsiFileGist<Integer> countingPsiGist() {
    int invocations = 0
    return GistManager.instance.newPsiFileGist(getTestName(true) + ' psi', 0, EnumeratorIntegerDescriptor.INSTANCE, { f -> ++invocations })
  }

  void "test invalidateData works for non-physical files"() {
    def psiFile = PsiFileFactory.getInstance(project).createFileFromText('a.txt', PlainTextFileType.INSTANCE, 'foo bar')
    def vFile = psiFile.viewProvider.virtualFile
    def vfsGist = countingVfsGist()
    def psiGist = countingPsiGist()

    assert 1 == vfsGist.getFileData(project, vFile)
    assert 1 == psiGist.getFileData(psiFile)

    GistManager.instance.invalidateData()
    assert 2 == vfsGist.getFileData(project, vFile)
    assert 2 == psiGist.getFileData(psiFile)
  }

  void "test data is recalculated when ancestor directory changes"() {
    def gist = countingVfsGist()
    def file = myFixture.addFileToProject('foo/bar/a.txt', '').virtualFile
    assert 1 == gist.getFileData(project, file)

    WriteAction.run { file.parent.parent.rename(this, 'goo') }
    assert 2 == gist.getFileData(project, file)
  }

}
