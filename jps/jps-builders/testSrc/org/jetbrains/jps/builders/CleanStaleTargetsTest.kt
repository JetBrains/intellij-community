// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders

import com.intellij.util.PathUtil
import com.intellij.util.io.directoryContent
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil

class CleanStaleTargetsTest : JpsBuildTestCase() {
  fun `test delete old output when module is deleted`() {
    //todo[nik, jeka] currently references to classes from deleted module aren't removed ClassToSubclasses, ClassToClassDependency, SourceFileToClasses mappings
    doTestDeleteOldOutput(false) {
      myProject.removeModule(it)
    }
  }

  fun `test delete old output when module is renamed`() {
    doTestDeleteOldOutput {
      it.name = "a2"
    }
  }

  fun `test delete old output when module output is changed`() {
    doTestDeleteOldOutput {
      val moduleExtension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(it)
      moduleExtension.isInheritOutput = false
      moduleExtension.outputUrl = JpsPathUtil.pathToUrl(getAbsolutePath("out/a2"))
    }
  }

  private fun doTestDeleteOldOutput(checkMappings: Boolean = true, action: (JpsModule) -> Unit) {
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myProject).outputUrl = JpsPathUtil.pathToUrl(getAbsolutePath("out"))
    val aRoot = PathUtil.getParentPath(createFile("a/src/A.java", "class A {}"))
    val aModule = addModule("a", arrayOf(aRoot), null, null, jdk)
    val bRoot = PathUtil.getParentPath(createFile("b/src/B.java", "class B {}"))
    val bModule = addModule("b", arrayOf(bRoot), null, null, jdk)
    rebuildAllModules()

    val aOutput = getModuleOutput(aModule)
    assertOutput(aOutput.absolutePath, directoryContent { file("A.class") })
    action(aModule)

    doBuild(CompileScopeTestBuilder.make().module(bModule))
    //do not clean output when just one other target is built to avoid unexpectedly long builds
    assertOutput(aOutput.absolutePath, directoryContent { file("A.class") })

    val buildResult = buildAllModules()
    //clean output of stale targets when all targets of this type are built
    if (aOutput.exists()) {
      assertOutput(aOutput.absolutePath, directoryContent { })
    }

    if (checkMappings) {
      checkMappingsAreSameAfterRebuild(buildResult)
    }
  }
}
