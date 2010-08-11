package com.intellij.psi.impl.cache.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestCase;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class SCR17650Test extends PsiTestCase {
  private static final String TEST_ROOT = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/cls";

  private VirtualFile myDir;

  protected void setUp() throws Exception {
    super.setUp();

    final File root = File.createTempFile(getName(), "");
    root.delete();
    root.mkdir();
    myFilesToDelete.add(root);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

          myDir = rootVFile.createChildDirectory(null, "contentAndLibrary");

          VirtualFile file1 = myDir.createChildData(null, "A.java");
          VfsUtil.saveText(file1, "package p; public class A{ public void foo(); }");
          VfsUtil.copyFile(null, getClassFile(), myDir);

          final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
          final ContentEntry contentEntry1 = rootModel.addContentEntry(myDir);
          contentEntry1.addSourceFolder(myDir, false);
          final Library jarLibrary = rootModel.getModuleLibraryTable().createLibrary();
          final Library.ModifiableModel libraryModel = jarLibrary.getModifiableModel();
          libraryModel.addRoot(myDir, OrderRootType.CLASSES);
          libraryModel.commit();
          rootModel.commit();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    });
  }

  private static VirtualFile getClassFile() {
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByPath(TEST_ROOT.replace(File.separatorChar, '/'));
    VirtualFile child = vDir.findChild("pack").findChild("MyClass.class");
    return child;
  }

  public void test17650() throws Exception {
    assertEquals("p.A", myJavaFacade.findClass("p.A").getQualifiedName());
    assertEquals("pack.MyClass", myJavaFacade.findClass("pack.MyClass").getQualifiedName());
  }
}
