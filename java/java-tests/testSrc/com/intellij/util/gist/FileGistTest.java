// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.;
package com.intellij.util.gist;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.ref.GCWatcher;

import java.io.IOException;

public class FileGistTest extends LightJavaCodeInsightFixtureTestCase {

  public void testGetData() {
    VirtualFileGist<String> gist = take3Gist();
    assert "foo".equals(gist.getFileData(getProject(), addFooBarFile()));
    assert "bar".equals(gist.getFileData(getProject(), myFixture.addFileToProject("b.txt", "bar foo").getVirtualFile()));
  }

  private VirtualFile addFooBarFile() {
    return myFixture.addFileToProject("a.txt", "foo bar").getVirtualFile();
  }

  private VirtualFileGist<String> take3Gist() {
    return GistManager.getInstance().newVirtualFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, (p, f) -> LoadTextUtil.loadText(f).toString().substring(0, 3));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ((GistManagerImpl)GistManager.getInstance()).resetReindexCount();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testDataIsCachedPerFile() {
    VirtualFileGist<Integer> gist = countingVfsGist();

    VirtualFile file = addFooBarFile();
    assert gist.getFileData(getProject(), file) == 1;
    assert gist.getFileData(getProject(), file) == 1;

    assert gist.getFileData(getProject(), myFixture.addFileToProject("b.txt", "").getVirtualFile()) == 2;
  }

  private VirtualFileGist<Integer> countingVfsGist() {
    return GistManager.getInstance().newVirtualFileGist(getTestName(true), 0, EnumeratorIntegerDescriptor.INSTANCE, countingCalculator());
  }

  public void testDataIsRecalculatedOnFileChange() throws IOException {
    VirtualFileGist<Integer> gist = countingVfsGist();
    VirtualFile file = addFooBarFile();
    assert gist.getFileData(getProject(), file) == 1;

    WriteAction.run(()-> VfsUtil.saveText(file, "x"));
    assert gist.getFileData(getProject(), file) == 2;

    FileContentUtilCore.reparseFiles(file);
    assert gist.getFileData(getProject(), file) == 3;

    PushedFilePropertiesUpdater.getInstance(getProject()).filePropertiesChanged(file, Conditions.alwaysTrue());
    assert gist.getFileData(getProject(), file) == 4;
  }

  public void testDataIsNotRecalculatedOnAnotherFileChange() throws IOException {
    VirtualFileGist<Integer> gist = countingVfsGist();
    VirtualFile file1 = addFooBarFile();
    VirtualFile file2 = myFixture.addFileToProject("b.txt", "foo bar").getVirtualFile();
    assert gist.getFileData(getProject(), file1) == 1;
    assert gist.getFileData(getProject(), file2) == 2;

    WriteAction.run(()-> VfsUtil.saveText(file1, "x"));
    assert gist.getFileData(getProject(), file2) == 2;
  }

  public void testVfsGistWorksForLightFiles() {
    assert "goo".equals(take3Gist().getFileData(getProject(), new LightVirtualFile("a.txt", "goo goo")));
  }

  public void testDifferentDataForDifferentGetProjects() {
    final int[] invocations = {0};
    VirtualFileGist<String> gist = GistManager.getInstance().newVirtualFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, (p, f) -> {
      ++invocations[0];
      return (p==null?null:p.getName()) + " " + invocations[0];
    });
    VirtualFile file = addFooBarFile();

    String name = getProject().getName();
    assert (name+" 1").equals(gist.getFileData(getProject(), file));
    String defName = ProjectManager.getInstance().getDefaultProject().getName();
    assert (defName+" 2").equals(gist.getFileData(ProjectManager.getInstance().getDefaultProject(), file));
    assert (name+" 1").equals(gist.getFileData(getProject(), file));
    assert "null 3".equals(gist.getFileData(null, file));
  }

  public void testCannotRegisterTwice() {
    take3Gist();
    try {
      take3Gist();
      fail();
    }
    catch (IllegalArgumentException ignore) {
    }
  }

  private int invocations;
  private VirtualFileGist.GistCalculator<Integer> countingCalculator() {
    return (p, f) -> ++invocations;
  }

  public void testNullData() {
    invocations = 0;
    VirtualFileGist<String> gist = GistManager.getInstance().newVirtualFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, (p, f) -> {invocations++; return null;});
    VirtualFile file = addFooBarFile();
    assert null == gist.getFileData(getProject(), file);
    assert null == gist.getFileData(getProject(), file);
    assert invocations == 1;
  }

  public void testPsiGistUsesLastCommittedDocumentContent() throws IOException {
    PsiFile file = myFixture.addFileToProject("a.txt", "foo bar");
    PsiFileGist<String> gist = GistManager.getInstance().newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p-> p.getText().substring(0, 3));
    assert "foo".equals(gist.getFileData(file));

    WriteAction.run(()-> {
      VfsUtil.saveText(file.getVirtualFile(), "bar foo");
      assert file.isValid();
      assert PsiDocumentManager.getInstance(getProject()).isUncommited(file.getViewProvider().getDocument());
      assert "foo".equals(gist.getFileData(file));
    });

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    assert "bar".equals(gist.getFileData(file));
  }


  public void testPsiGistDoesNotLoadAST() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("a.java", "package bar;");
    assert !file.isContentsLoaded();

    assert GistManager.getInstance()
      .newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p -> p.findElementAt(0).getText()).getFileData(file)
      .equals("package");
    assert !file.isContentsLoaded();
  }

  public void testPsiGistWorkForBinaryFiles() {
    PsiFile objectClass = JavaPsiFacade.getInstance(getProject()).findClass(Object.class.getName(), GlobalSearchScope.allScope(getProject())).getContainingFile();

    assert GistManager.getInstance().newPsiFileGist(getTestName(true), 0, EnumeratorStringDescriptor.INSTANCE, p -> p.getName())
      .getFileData(objectClass).equals("Object.class");
  }

  public void testPsiGistDoesNotLoadDocument() {
    PsiFileGist<Integer> gist = countingPsiGist();
    PsiFile file = myFixture.addFileToProject("a.xtt", "foo");
    assert gist.getFileData(file) == 1;

    GCWatcher.tracking(PsiDocumentManager.getInstance(getProject()).getCachedDocument(file)).ensureCollected();
    assert PsiDocumentManager.getInstance(getProject()).getCachedDocument(file) == null;

    assert gist.getFileData(file) == 1;
    assert PsiDocumentManager.getInstance(getProject()).getCachedDocument(file) == null;
  }

  private PsiFileGist<Integer> countingPsiGist() {
    int[] invocations = {0};
    return GistManager.getInstance().newPsiFileGist(getTestName(true) + " psi", 0, EnumeratorIntegerDescriptor.INSTANCE, p -> ++invocations[0]);
  }

  public void testInvalidateDataWorksForNonPhysicalFiles() {
    PsiFile psiFile = PsiFileFactory.getInstance(getProject()).createFileFromText("a.txt", PlainTextFileType.INSTANCE, "foo bar");
    VirtualFile vFile = psiFile.getViewProvider().getVirtualFile();
    VirtualFileGist<Integer> vfsGist = countingVfsGist();
    PsiFileGist<Integer> psiGist = countingPsiGist();

    assert 1 == vfsGist.getFileData(getProject(), vFile);
    assert 1 == psiGist.getFileData(psiFile) : psiGist.getFileData(psiFile);

    GistManager.getInstance().invalidateData();
    assert 2 == vfsGist.getFileData(getProject(), vFile);
    assert 2 == psiGist.getFileData(psiFile);
  }

  public void testDataIsRecalculatedWhenAncestorDirectoryChanges() throws IOException {
    VirtualFileGist<Integer> gist = countingVfsGist();
    VirtualFile file = myFixture.addFileToProject("foo/bar/a.txt", "").getVirtualFile();
    assert 1 == gist.getFileData(getProject(), file);
    WriteAction.run(()-> file.getParent().getParent().rename(this, "goo"));
    assert 2 == gist.getFileData(getProject(), file);
  }
}
