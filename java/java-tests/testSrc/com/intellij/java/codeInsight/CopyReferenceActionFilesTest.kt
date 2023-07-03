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

import com.intellij.codeInsight.JavaCodeInsightTestCase
import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PsiTestUtil
import junit.framework.TestCase

internal class CopyReferenceActionFilesTest : JavaCodeInsightTestCase() {
  lateinit var additionalRoot: VirtualFile
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    module.setModuleType(JavaModuleType.getModuleType().id)
    ApplicationManager.getApplication().runWriteAction(object : Runnable {
      override fun run() {
        additionalRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!.createChildDirectory(this, "newRoot")
      }
    })
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      ApplicationManager.getApplication().runWriteAction(
        Runnable { additionalRoot.delete(this) })
    }
    finally {
      super.tearDown()
    }
  }

  @Throws(Exception::class)
  fun testCopyFile_RegisteredAsSourceRoots_ShouldContainItsName() {
    // CPP-4315 "Edit | Copy Reference" result doesn't contain the file name
    lateinit var dir: VirtualFile
    lateinit var dir_subfile: VirtualFile
    lateinit var file: VirtualFile
    ApplicationManager.getApplication().runWriteAction(object : Runnable {
      override fun run() {
        dir = additionalRoot.createChildDirectory(this, "dir")
        dir_subfile = dir.createChildData(this, "dir_subfile.txt")
        file = additionalRoot.createChildData(this, "file.txt")
        PsiTestUtil.addContentRoot(getModule(), additionalRoot)
        PsiTestUtil.addSourceRoot(getModule(), dir)
        PsiTestUtil.addSourceRoot(getModule(), file)
      }
    })
    TestCase.assertEquals("dir", CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findDirectory(dir)))
    TestCase.assertEquals("dir_subfile.txt",
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findFile(dir_subfile)))
    TestCase.assertEquals("file.txt", CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findFile(file)))
  }

  @Throws(Exception::class)
  fun testCopyFile_RegisteredAsContentRoot_ShouldContainItsFullPath() {
    // IDEA-144300 Copy Reference for source folder/content root copies empty string
    lateinit var dir: VirtualFile
    lateinit var dir_subfile: VirtualFile
    lateinit var file: VirtualFile
    ApplicationManager.getApplication().runWriteAction(object : Runnable {
      override fun run() {
        dir = additionalRoot.createChildDirectory(this, "dir")
        dir_subfile = dir.createChildData(this, "dir_subfile.txt")
        file = additionalRoot.createChildData(this, "file.txt")
        PsiTestUtil.addContentRoot(getModule(), dir)
        PsiTestUtil.addContentRoot(getModule(), file)
      }
    })
    TestCase.assertEquals(dir.getPath(),
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findDirectory(dir)))
    TestCase.assertEquals("dir_subfile.txt",
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findFile(dir_subfile)))
    TestCase.assertEquals(file.getPath(), CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findFile(file)))
  }

  @Throws(Exception::class)
  fun testCopyFile_RegisteredAsNestedContentRoot_ShouldContainPathFromOuterMostRoot() {
    // IDEA-144300 Copy Reference for source folder/content root copies empty string
    lateinit var dir: VirtualFile
    lateinit var dir_dir: VirtualFile
    lateinit var dir_dir_file: VirtualFile
    ApplicationManager.getApplication().runWriteAction(object : Runnable {
      override fun run() {
        dir = additionalRoot.createChildDirectory(this, "dir")
        dir_dir = dir.createChildDirectory(this, "dir_dir")
        dir_dir_file = dir_dir.createChildData(this, "file.txt")
        PsiTestUtil.addContentRoot(getModule(), dir)
        PsiTestUtil.addContentRoot(getModule(), dir_dir)
      }
    })
    TestCase.assertEquals(dir.getPath(),
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findDirectory(dir)))
    TestCase.assertEquals("dir_dir",
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findDirectory(dir_dir)))
    TestCase.assertEquals("dir_dir/file.txt",
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findFile(dir_dir_file)))
  }

  @Throws(Exception::class)
  fun testCopyFile_UnderExcludeRoot_ShouldContainPathFromTheCorrespondingContentRoot() {
    // IDEA-144316 Copy Reference should work for excluded subfolders same way as it works for regular project subdirs
    lateinit var dir: VirtualFile
    lateinit var dir_dir: VirtualFile
    lateinit var dir_dir_file: VirtualFile
    ApplicationManager.getApplication().runWriteAction(object : Runnable {
      override fun run() {
        dir = additionalRoot.createChildDirectory(this, "dir")
        dir_dir = dir.createChildDirectory(this, "dir_dir")
        dir_dir_file = dir_dir.createChildData(this, "file.txt")
        PsiTestUtil.addContentRoot(getModule(), dir)
        PsiTestUtil.addExcludedRoot(getModule(), dir_dir)
      }
    })
    TestCase.assertEquals(dir.getPath(),
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findDirectory(dir)))
    TestCase.assertEquals("dir_dir",
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findDirectory(dir_dir)))
    TestCase.assertEquals("dir_dir/file.txt",
                          CopyReferenceAction.elementToFqn(com.intellij.psi.PsiManager.getInstance(project).findFile(dir_dir_file)))
  }
}
