// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.util.*

@ApiStatus.Internal
fun checkFileNameInput(inputString: String, fileName: String, directory: PsiDirectory): String? {
  val tokenizer = StringTokenizer(inputString, "\\/")
  var vFile: VirtualFile? = directory.getVirtualFile()
  var firstToken = true
  while (tokenizer.hasMoreTokens()) {
    val token = tokenizer.nextToken()
    if ((token == "." || token == "..") && !tokenizer.hasMoreTokens()) {
      return IdeBundle.message("error.invalid.file.name", token)
    }
    if (vFile != null) {
      if (firstToken && "~" == token) {
        val userHomeDir = VfsUtil.getUserHomeDir()
        if (userHomeDir == null) {
          return IdeBundle.message("error.user.home.directory.not.found")
        }
        vFile = userHomeDir
      }
      else if (".." == token) {
        val parent = vFile.getParent()
        if (parent == null) {
          return IdeBundle.message("error.invalid.directory", vFile.getPresentableUrl() + File.separatorChar + "..")
        }
        vFile = parent
      }
      else if ("." != token) {
        val child = vFile.findChild(token)
        if (child != null) {
          if (!child.isDirectory()) {
            return IdeBundle.message("error.file.with.name.already.exists", token)
          }
          else if (!tokenizer.hasMoreTokens()) {
            return IdeBundle.message("error.directory.with.name.already.exists", token)
          }
        }
        vFile = child
      }
    }
    if (FileTypeManager.getInstance().isFileIgnored(fileName)) {
      return IdeBundle.message("warning.create.directory.with.ignored.name", token)
    }
    firstToken = false
  }
  return null
}