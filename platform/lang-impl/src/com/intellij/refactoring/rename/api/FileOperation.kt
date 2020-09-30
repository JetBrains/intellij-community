// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.api

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.text.StringOperation
import java.nio.file.Path

sealed class FileOperation {

  internal class Add(val path: Path, val content: CharSequence) : FileOperation()
  internal class Move(val file: VirtualFile, val path: Path) : FileOperation()
  internal class Remove(val file: VirtualFile) : FileOperation()
  internal class Modify(val file: PsiFile, val modifications: Collection<StringOperation>) : FileOperation() {
    init {
      require(modifications.isNotEmpty())
    }
  }

  companion object {

    @JvmStatic
    fun addFile(path: Path, content: CharSequence): FileOperation = Add(path, content)

    @JvmStatic
    fun moveFile(file: VirtualFile, path: Path): FileOperation = Move(file, path)

    @JvmStatic
    fun removeFile(file: VirtualFile): FileOperation = Remove(file)

    @JvmStatic
    fun modifyFile(file: PsiFile, modifications: Collection<StringOperation>): FileOperation = Modify(file, modifications)

    @JvmStatic
    fun modifyFile(file: PsiFile, modification: StringOperation, vararg modifications: StringOperation): FileOperation {
      return modifyFile(file, listOf(modification, *modifications))
    }
  }
}
