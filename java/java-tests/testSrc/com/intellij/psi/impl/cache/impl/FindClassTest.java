/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author max
 */
public class FindClassTest extends PsiTestCase {
  private VirtualFile myPrjDir1;
  private VirtualFile mySrcDir1;
  private VirtualFile myPackDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile rootVFile =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

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

  public void testSimple() throws Exception {
    PsiClass psiClass = myJavaFacade.findClass("p.A");
    assertEquals("p.A", psiClass.getQualifiedName());
  }

  public void testClassUnderExcludedFolder() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiTestUtil.addExcludedRoot(myModule, myPackDir);

        PsiClass psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
        assertNull(psiClass);

        ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
        final ContentEntry content = rootModel.getContentEntries()[0];
        content.removeExcludeFolder(content.getExcludeFolders()[0]);
        rootModel.commit();

        psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
        assertEquals("p.A", psiClass.getQualifiedName());
      }
    });
  }

  public void testClassUnderIgnoredFolder() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        PsiClass psiClass = myJavaFacade.findClass("p.A", GlobalSearchScope.allScope(myProject));
        assertEquals("p.A", psiClass.getQualifiedName());

        assertTrue(psiClass.isValid());

        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        String ignoredFilesList = fileTypeManager.getIgnoredFilesList();
        fileTypeManager.setIgnoredFilesList(ignoredFilesList + ";p");
        try {
          assertFalse(psiClass.isValid());
        }
        finally {
          fileTypeManager.setIgnoredFilesList(ignoredFilesList);
        }

        psiClass = myJavaFacade.findClass("p.A");
        assertTrue(psiClass.isValid());
      }
    });
  }

  public void testSynchronizationAfterChange() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
        PsiClass psiClass = myJavaFacade.findClass("p.A");
        final VirtualFile vFile = psiClass.getContainingFile().getVirtualFile();
        File ioFile = VfsUtil.virtualToIoFile(vFile);
        ioFile.setLastModified(5);

        LocalFileSystem.getInstance().refresh(false);

        ModuleRootModificationUtil.setModuleSdk(myModule, null);

        psiClass = myJavaFacade.findClass("p.A");
        assertNotNull(psiClass);
      }
    });
  }
}
