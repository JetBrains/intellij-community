/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight

import com.intellij.codeInsight.CodeInsightTestCase
import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil

class CopyReferenceActionFilesTest extends CodeInsightTestCase {
  VirtualFile additionalRoot

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    getModule().setModuleType(JavaModuleType.getModuleType().getId())
    ApplicationManager.application.runWriteAction(new Runnable() {
      @Override
      void run() {
        additionalRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///").createChildDirectory(this, "newRoot")
      }
    })
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ApplicationManager.application.runWriteAction(new Runnable() {
        @Override
        void run() {
          additionalRoot.delete(this)
        }
      })
    }
    finally {
      super.tearDown()
    }
  }

  void testCopyFile_RegisteredAsSourceRoots_ShouldContainItsName() throws Exception {
    // CPP-4315 "Edit | Copy Reference" result doesn't contain the file name
    
    VirtualFile dir
    VirtualFile dir_subfile
    VirtualFile file

    ApplicationManager.application.runWriteAction(new Runnable() {
      @Override
      void run() {
        dir = additionalRoot.createChildDirectory(this, "dir")
        dir_subfile = dir.createChildData(this, "dir_subfile.txt")
        file = additionalRoot.createChildData(this, "file.txt")

        PsiTestUtil.addContentRoot(getModule(), additionalRoot)
        PsiTestUtil.addSourceRoot(getModule(), dir)
        PsiTestUtil.addSourceRoot(getModule(), file)
      }
    })

    assertEquals("dir", CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findDirectory(dir)))
    assertEquals("dir_subfile.txt", CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findFile(dir_subfile)))
    assertEquals("file.txt", CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findFile(file)))
  }

  void testCopyFile_RegisteredAsContentRoot_ShouldContainItsFullPath() throws Exception {
    // IDEA-144300 Copy Reference for source folder/content root copies empty string
    
    VirtualFile dir
    VirtualFile dir_subfile
    VirtualFile file

    ApplicationManager.application.runWriteAction(new Runnable() {
      @Override
      void run() {
        dir = additionalRoot.createChildDirectory(this, "dir")
        dir_subfile = dir.createChildData(this, "dir_subfile.txt")
        file = additionalRoot.createChildData(this, "file.txt")

        PsiTestUtil.addContentRoot(getModule(), dir)
        PsiTestUtil.addContentRoot(getModule(), file)
      }
    })

    assertEquals(dir.getPath(), CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findDirectory(dir)))
    assertEquals("dir_subfile.txt", CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findFile(dir_subfile)))
    assertEquals(file.getPath(), CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findFile(file)))
  }

  void testCopyFile_RegisteredAsNestedContentRoot_ShouldContainPathFromOuterMostRoot() throws Exception {
    // IDEA-144300 Copy Reference for source folder/content root copies empty string
    
    VirtualFile dir
    VirtualFile dir_dir
    VirtualFile dir_dir_file

    ApplicationManager.application.runWriteAction(new Runnable() {
      @Override
      void run() {
        dir = additionalRoot.createChildDirectory(this, "dir")
        dir_dir = dir.createChildDirectory(this, "dir_dir")
        dir_dir_file = dir_dir.createChildData(this, "file.txt")

        PsiTestUtil.addContentRoot(getModule(), dir)
        PsiTestUtil.addContentRoot(getModule(), dir_dir)
      }
    })

    assertEquals(dir.getPath(), CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findDirectory(dir)))
    assertEquals("dir_dir", CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findDirectory(dir_dir)))
    assertEquals("dir_dir/file.txt", CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findFile(dir_dir_file)))
  }

  void testCopyFile_UnderExcludeRoot_ShouldContainPathFromTheCorrespondingContentRoot() throws Exception {
    // IDEA-144316 Copy Reference should work for excluded subfolders same way as it works for regular project subdirs
    
    VirtualFile dir
    VirtualFile dir_dir
    VirtualFile dir_dir_file

    ApplicationManager.application.runWriteAction(new Runnable() {
      @Override
      void run() {
        dir = additionalRoot.createChildDirectory(this, "dir")
        dir_dir = dir.createChildDirectory(this, "dir_dir")
        dir_dir_file = dir_dir.createChildData(this, "file.txt")

        PsiTestUtil.addContentRoot(getModule(), dir)
        PsiTestUtil.addExcludedRoot(getModule(), dir_dir)
      }
    })

    assertEquals(dir.getPath(), CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findDirectory(dir)))
    assertEquals("dir_dir", CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findDirectory(dir_dir)))
    assertEquals("dir_dir/file.txt", CopyReferenceAction.elementToFqn(PsiManager.getInstance(project).findFile(dir_dir_file)))
  }
}
