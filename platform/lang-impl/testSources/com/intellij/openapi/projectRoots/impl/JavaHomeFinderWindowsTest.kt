// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("SpellCheckingInspection")

package com.intellij.openapi.projectRoots.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories

class JavaHomeFinderWindowsTest {
  @get:Rule
  val tempDir = TemporaryFolder()

  @Test
  fun `parse basic Reg output`() {
    val text = """|
                  |HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\JDK\9.0.4
                  |    JavaHome    REG_SZ    C:\Java\1.9
                  |
                  |HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\JDK\11.0.6
                  |    JavaHome    REG_SZ    D:\Java\11.0.6
                  |
                  |HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\JDK\13.0.2
                  |    JavaHome    REG_SZ    F:\AnotherFolder\OracleJava\13.0.2
                  |
                  |End of search: 3 match(es) found.
                  |
    """.trimMargin()
    val paths = JavaHomeFinderWindows.gatherHomePaths(text)

    assertThat(paths).containsExactly("""C:\Java\1.9""", """D:\Java\11.0.6""", """F:\AnotherFolder\OracleJava\13.0.2""")
  }

  @Test
  fun `find JDK in vendor dir under Program Files`() {
    val fsRoot = tempDir.newFolder("C").toPath()
    val jdkHome = fsRoot.resolve("Program Files/Microsoft/jdk-17.0.21.9-hotspot")
    // `javac` or `javac.exe` is checked depending on the OS of the path; create both.
    for (file in listOf("bin/javac", "bin/javac.exe", "lib/jrt-fs.jar")) {
      jdkHome.resolve(file).createParentDirectories().createFile()
    }
    // A vendor dir child that is not a JDK must be skipped.
    fsRoot.resolve("Program Files/Microsoft/Edge").createDirectories()

    val systemInfo = object : JavaHomeFinder.SystemInfoProvider() {
      override fun getEnvironmentVariable(name: String): String? = null
      override fun getUserHome(): Path = tempDir.root.toPath().resolve("home")
      override fun getFsRoots(): Collection<Path> = listOf(fsRoot)
    }
    val finder = JavaHomeFinderWindows(registeredJdks = false, wslJdks = false, systemInfoProvider = systemInfo, processRunner = { "" })

    assertThat(finder.findExistingJdks()).containsExactly(jdkHome.toAbsolutePath().toString())
  }
}