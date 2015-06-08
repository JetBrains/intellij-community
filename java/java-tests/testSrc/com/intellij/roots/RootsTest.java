package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

/**
 *  @author dsl
 */
public class RootsTest extends PsiTestCase {
  public void testTest1() {
    final String rootPath = PathManagerEx.getTestDataPath() + "/moduleRootManager/roots/" + "test1";
    final VirtualFile[] rootFileBox = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(() -> {
      rootFileBox[0] =
      LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath.replace(File.separatorChar, '/'));
    });
    final VirtualFile rootFile = rootFileBox[0];
    final VirtualFile classesFile = rootFile.findChild("classes");
    assertNotNull(classesFile);
    final VirtualFile childOfContent = rootFile.findChild("x.txt");
    assertNotNull(childOfContent);
    final VirtualFile childOfClasses = classesFile.findChild("y.txt");
    assertNotNull(childOfClasses);

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);


    PsiTestUtil.addContentRoot(myModule, rootFile);
    PsiTestUtil.setCompilerOutputPath(myModule, classesFile.getUrl(), false);
    PsiTestUtil.setExcludeCompileOutput(myModule, false);
    assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
    assertTrue(rootManager.getFileIndex().isInContent(childOfClasses));

    PsiTestUtil.setExcludeCompileOutput(myModule, true);
    assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
    assertFalse(rootManager.getFileIndex().isInContent(childOfClasses));
  }

}
