package com.intellij.psi;

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
}
