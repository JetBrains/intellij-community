package com.intellij.psi.resolve;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.include.FileIncludeInfo;
import com.intellij.psi.impl.include.FileIncludeManager;

/**
 * @author cdr
 */
public class FilePathResolveTest extends CodeInsightTestCase{
  private static final String BASE_PATH = "/psi/resolve/filePath/";

  public void testC1() throws Exception{
    configure("C.java");
    checkNavigatesTo("MyClass.java");
  }
  public void testC2() throws Exception{
    configure("C2.java");
    checkNavigatesTo("MyFile.txt");
  }

  public void testResolveFileReference() throws Exception {
    configureByFile(BASE_PATH + "C.java", BASE_PATH);
    FileIncludeManager fileIncludeManager = FileIncludeManager.getManager(getProject());
    PsiFileSystemItem item = fileIncludeManager.resolveFileInclude(new FileIncludeInfo("x/MyFile.txt"), getFile());
    assertNotNull(item);
    assertEquals("MyFile.txt", item.getName());
  }

  private void checkNavigatesTo(String expected) {
    final int offset = myEditor.getCaretModel().getOffset();
    final PsiElement targetElement = GotoDeclarationAction.findTargetElement(myProject, myEditor, offset);
    assertEquals(expected, ((PsiFile)targetElement).getName());
  }

  private void configure(final String fileName) throws Exception {
    configureByFile(BASE_PATH + fileName, BASE_PATH);
  }
}
