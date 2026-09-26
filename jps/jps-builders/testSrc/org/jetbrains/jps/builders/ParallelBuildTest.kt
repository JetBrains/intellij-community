// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders

import com.intellij.util.PathUtil
import org.jetbrains.jps.api.GlobalOptions

class ParallelBuildTest: JpsBuildTestCase() {
  override fun setUp() {
    super.setUp()
    System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, true.toString())
  }

  override fun tearDown() {
    try {
      System.setProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, false.toString())
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testBuildDependentModules() {
    createFile("m1/Class0.java", "public class Class0 {}")
    for (i in 1..10) {
      val file = createFile("m$i/Class$i.java", "public class Class$i { Class${i-1} prev; }")
      val module = addModule("m$i", PathUtil.getParentPath(file))
      if (i > 1) {
        module.dependenciesList.addModuleDependency(myProject.findModuleByName("m${i-1}")!!)
      }
    }
    rebuildAllModules()
  }
}