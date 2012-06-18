package com.intellij.psi.impl.cache.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class SCR20733Test extends PsiTestCase {
  private VirtualFile myPrjDir1;
  private VirtualFile mySrcDir1;
  private VirtualFile myPackDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = FileUtil.createTempFile(getName(), "");
    root.delete();
    root.mkdir();
    myFilesToDelete.add(root);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

          myPrjDir1 = rootVFile.createChildDirectory(null, "prj1");
          mySrcDir1 = myPrjDir1.createChildDirectory(null, "src1");

          myPackDir = mySrcDir1.createChildDirectory(null, "p");
          VirtualFile file1 = myPackDir.createChildData(null, "A.java");
          VfsUtil.saveText(file1, "package p; public class A{ public void foo(); }");

          PsiTestUtil.addContentRoot(myModule, myPrjDir1);
          PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  public void testBug() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiClass psiClass = myJavaFacade.findClass("p.A");
        assertEquals("p.A", psiClass.getQualifiedName());

        final PsiFile psiFile = myPsiManager.findFile(myPackDir.findChild("A.java"));
        psiFile.getChildren();
        assertEquals(psiFile, psiClass.getContainingFile());

        VirtualFile file = psiFile.getVirtualFile();
        assertEquals(myModule, ModuleUtil.findModuleForFile(file, myProject));

        Module anotherModule = createModule("another");
        myFilesToDelete.add(new File(anotherModule.getModuleFilePath()));

        PsiTestUtil.addSourceRoot(anotherModule, mySrcDir1);

        assertEquals(anotherModule, ModuleUtil.findModuleForFile(file, myProject));
      }
    });
  }
}
