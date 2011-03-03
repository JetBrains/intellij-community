package com.intellij.psi;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

@PlatformTestCase.WrapInCommand
public class AddClassToFileTest extends PsiTestCase{
  public void test() throws Exception {
    VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, myFilesToDelete);
    PsiDirectory dir = myPsiManager.findDirectory(root);
    PsiFile file = dir.createFile("AAA.java");
    PsiClass aClass = myJavaFacade.getElementFactory().createClass("AAA");
    file.add(aClass);

    PsiTestUtil.checkFileStructure(file);
  }

  public void testFileModified() throws Exception {
    VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, myFilesToDelete);
    VirtualFile pkg = root.createChildDirectory(this, "foo");
    PsiDirectory dir = myPsiManager.findDirectory(pkg);
    String text = "package foo;\n\nclass A {}";
    PsiElement created = dir.add(PsiFileFactory.getInstance(getProject()).createFileFromText("A.java", text));
    VirtualFile virtualFile = created.getContainingFile().getVirtualFile();
    String fileText = LoadTextUtil.loadText(virtualFile).toString();
    assertEquals(text, fileText);

    Document doc = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(doc));
    assertFalse(FileDocumentManager.getInstance().isFileModified(virtualFile));
  }
}
