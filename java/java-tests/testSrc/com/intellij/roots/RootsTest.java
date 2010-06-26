package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;

/**
 *  @author dsl
 */
public class RootsTest extends PsiTestCase {
  public void testTest1() {
    final String rootPath = PathManagerEx.getTestDataPath() + "/moduleRootManager/roots/" + "test1";
    final VirtualFile[] rootFileBox = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootFileBox[0] =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath.replace(File.separatorChar, '/'));
      }
    });
    final VirtualFile rootFile = rootFileBox[0];
    final VirtualFile classesFile = rootFile.findChild("classes");
    assertNotNull(classesFile);
    final VirtualFile childOfContent = rootFile.findChild("x.txt");
    assertNotNull(childOfContent);
    final VirtualFile childOfClasses = classesFile.findChild("y.txt");
    assertNotNull(childOfClasses);

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);


    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        rootModel.addContentEntry(rootFile);
        rootModel.getModuleExtension(CompilerModuleExtension.class).inheritCompilerOutputPath(false);
        rootModel.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPath(classesFile);
        rootModel.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(false);
        rootModel.commit();
      }
    });
    assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
    assertTrue(rootManager.getFileIndex().isInContent(childOfClasses));
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        rootModel.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(true);
        rootModel.commit();
      }
    });
    assertTrue(rootManager.getFileIndex().isInContent(childOfContent));
    assertFalse(rootManager.getFileIndex().isInContent(childOfClasses));
  }

}
