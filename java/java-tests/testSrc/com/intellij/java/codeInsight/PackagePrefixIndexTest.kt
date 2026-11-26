// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class PackagePrefixIndexTest {
  private val myFixture by javaModuleCodeInsightFixture(PathManager.getHomePath())
  private val project get() = myFixture.project
  private val module get() = myFixture.module

  @Test
  fun testRootWithPackagePrefix(): Unit = timeoutRunBlocking {
    myFixture.addClass("package a.b; class A {}")

    val aPackage = readAction { JavaPsiFacade.getInstance(project).findPackage("a")!! }
    Assertions.assertEquals(1, readAction { aPackage.subPackages.size })

    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      model.contentEntries[0]!!.addSourceFolder(myFixture.tempDirFixture.findOrCreateDir("tests"), true, "a.c")
    }
    Assertions.assertEquals(2, readAction { aPackage.subPackages.size })
    
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.contentEntries[0]!!
      val sourceFolder = contentEntry.sourceFolders.find { it.isTestSource }!!
      sourceFolder.packagePrefix = "a.d"
    }

    Assertions.assertEquals(2, readAction { aPackage.subPackages.size })
    
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.contentEntries[0]!!
      val sourceFolder = contentEntry.sourceFolders.find { it.isTestSource }!!
      contentEntry.removeSourceFolder(sourceFolder)
    }

    Assertions.assertEquals(1, readAction { aPackage.subPackages.size })
  }
  
  @Test
  fun testRootWithPackagePrefixNoInitialState(): Unit = timeoutRunBlocking {
    myFixture.addClass("package a.b; class A {}")

    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      model.contentEntries[0]!!.addSourceFolder(myFixture.tempDirFixture.findOrCreateDir("tests"), true, "a.c")
    }
    val aPackage = readAction { JavaPsiFacade.getInstance(project).findPackage("a")!! }
    Assertions.assertEquals(2, readAction { aPackage.subPackages.size })
    
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.contentEntries[0]!!
      val sourceFolder = contentEntry.sourceFolders.find { it.isTestSource }!!
      sourceFolder.packagePrefix = "a.d"
    }

    Assertions.assertEquals(2, readAction { aPackage.subPackages.size })
    
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.contentEntries[0]!!
      val sourceFolder = contentEntry.sourceFolders.find { it.isTestSource }!!
      contentEntry.removeSourceFolder(sourceFolder)
    }

    Assertions.assertEquals(1, readAction { aPackage.subPackages.size })
  }
}
