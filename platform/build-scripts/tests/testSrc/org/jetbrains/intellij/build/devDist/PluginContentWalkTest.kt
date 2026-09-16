// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** The `xi:include` probe of [derivePluginContentClosure] over an in-memory project. */
class PluginContentWalkTest {
  @Test
  fun `an include a layout member holds resolves when the member is stated`(@TempDir root: Path) {
    // `language-server.plugins.sql` is the case: `AllDatabaseDialectsShared.xml` sits in `intellij.database.dialects.core`,
    // which the layout packs and the main module does not depend on.
    val project = JpsElementFactory.getInstance().createModel().project
    val main = project.addModule("intellij.demo", JpsJavaModuleType.INSTANCE)
    main.addResources(
      root = root.resolve("main"),
      "META-INF/plugin.xml" to "<idea-plugin xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"/META-INF/shared.xml\"/></idea-plugin>",
    )
    project.addModule("intellij.demo.dialects", JpsJavaModuleType.INSTANCE).addResources(
      root = root.resolve("dialects"),
      "META-INF/shared.xml" to "<idea-plugin><content><module name=\"intellij.demo.dialects.impl\"/></content></idea-plugin>",
    )
    val findModule: (String) -> JpsModule? = project::findModuleByName

    val unresolved = derivePluginContentClosure(module = main, findModule = findModule)!!
    assertThat(unresolved.moduleNames).isEmpty()
    assertThat(unresolved.unresolvedIncludes).containsExactly("META-INF/shared.xml")

    val resolved = derivePluginContentClosure(module = main, findModule = findModule, layoutMembers = listOf("intellij.demo.dialects"))!!
    assertThat(resolved.moduleNames).containsExactly("intellij.demo.dialects.impl")
    assertThat(resolved.unresolvedIncludes).isEmpty()
  }
}

private fun JpsModule.addResources(root: Path, vararg files: Pair<String, String>) {
  for ((path, text) in files) {
    val file = root.resolve(path)
    file.parent.createDirectories()
    file.writeText(text)
  }
  addSourceRoot(JpsPathUtil.pathToUrl(root.toString()), JavaResourceRootType.RESOURCE)
}
