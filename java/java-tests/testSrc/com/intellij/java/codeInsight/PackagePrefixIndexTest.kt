// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.junit.Assert

class PackagePrefixIndexTest : JavaCodeInsightFixtureTestCase() {

  fun testRootWithPackagePrefix() {
    myFixture.addClass("package a.b; class A {}")

    val aPackage = JavaPsiFacade.getInstance(project).findPackage("a")!!
    Assert.assertEquals(1, aPackage.subPackages.size)

    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      model.contentEntries[0]!!.addSourceFolder(myFixture.tempDirFixture.findOrCreateDir("tests"), true, "a.c")
    }
    Assert.assertEquals(2, aPackage.subPackages.size)
    
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.contentEntries[0]!!
      val sourceFolder = contentEntry.sourceFolders.find { it.isTestSource }!!
      sourceFolder.packagePrefix = "a.d"
    }

    Assert.assertEquals(2, aPackage.subPackages.size)
    
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.contentEntries[0]!!
      val sourceFolder = contentEntry.sourceFolders.find { it.isTestSource }!!
      contentEntry.removeSourceFolder(sourceFolder)
    }

    Assert.assertEquals(1, aPackage.subPackages.size)
  }
  
  fun testRootWithPackagePrefixNoInitialState() {
    myFixture.addClass("package a.b; class A {}")

    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      model.contentEntries[0]!!.addSourceFolder(myFixture.tempDirFixture.findOrCreateDir("tests"), true, "a.c")
    }
    val aPackage = JavaPsiFacade.getInstance(project).findPackage("a")!!
    Assert.assertEquals(2, aPackage.subPackages.size)
    
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.contentEntries[0]!!
      val sourceFolder = contentEntry.sourceFolders.find { it.isTestSource }!!
      sourceFolder.packagePrefix = "a.d"
    }

    Assert.assertEquals(2, aPackage.subPackages.size)
    
    ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel ->
      val contentEntry = model.contentEntries[0]!!
      val sourceFolder = contentEntry.sourceFolders.find { it.isTestSource }!!
      contentEntry.removeSourceFolder(sourceFolder)
    }

    Assert.assertEquals(1, aPackage.subPackages.size)
  }
}
