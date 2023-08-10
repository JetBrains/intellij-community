// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiDirectory
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.registerExtension
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.Assert
import org.junit.Test

class CreateDirectoryOrPackageActionTest : LightPlatformTestCase() {
  @Test
  fun `test no duplicates in suggested directories`() {
    ApplicationManager.getApplication().registerExtension(CreateDirectoryOrPackageAction.EP, object : CreateDirectoryCompletionContributor {
      override fun getDescription(): @Nls(capitalization = Nls.Capitalization.Sentence) String {
        return "Mock Source Directories"
      }

      override fun getVariants(directory: PsiDirectory): Collection<CreateDirectoryCompletionContributor.Variant> {
        return listOf(CreateDirectoryCompletionContributor.Variant("src\\main\\resources", JavaSourceRootType.SOURCE),
                      CreateDirectoryCompletionContributor.Variant("src\\main\\resources", JavaSourceRootType.SOURCE))
      }
    }, testRootDisposable)
    val action = CreateDirectoryOrPackageAction()
    val directory = psiManager.findDirectory(getSourceRoot())!!
    val items = action.collectSuggestedDirectories(directory)
    Assert.assertEquals(1, items.size)
  }
}