// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.Platform
import com.intellij.openapi.util.io.FileUtil
import org.assertj.core.api.SoftAssertions
import org.junit.Test

class TargetEnvironmentPathsTest {
  @Test
  fun `test findPathVariants`() {
    SoftAssertions.assertSoftly { softAssertions ->
      val pycharmProjectPathMappings = listOf(
        PathMapping(
          "\\\\wsl\$\\Ubuntu\\home\\wsluser\\Project Foo",
          "/home/wsluser/Project Foo"
        )
      )
      softAssertions.softAssertFindPathVariants(
        mappings = pycharmProjectPathMappings,
        sourcePath = "\\\\wsl\$\\Ubuntu\\home\\wsluser\\Project Foo\\Bar Dir\\Baz Script.py",
        sourcePathFun = PathMapping::localPath,
        sourceFileSeparator = Platform.WINDOWS.fileSeparator,
        destPathFun = PathMapping::targetPath,
        destFileSeparator = Platform.UNIX.fileSeparator,
        describedAs = "Conversion from Windows UNC path in WSL to the corresponding Linux path",
        isEqualTo = listOf("/home/wsluser/Project Foo/Bar Dir/Baz Script.py")
      )
      softAssertions.softAssertFindPathVariants(
        mappings = pycharmProjectPathMappings,
        sourcePath = "/home/wsluser/Project Foo/Bar Dir/Baz Script.py",
        sourcePathFun = PathMapping::targetPath,
        sourceFileSeparator = Platform.UNIX.fileSeparator,
        destPathFun = PathMapping::localPath,
        destFileSeparator = Platform.WINDOWS.fileSeparator,
        describedAs = "Conversion from Linux path in WSL to the corresponding Windows UNC path",
        isEqualTo = listOf("\\\\wsl\$\\Ubuntu\\home\\wsluser\\Project Foo\\Bar Dir\\Baz Script.py")
      )
    }
  }

  companion object {
    private fun SoftAssertions.softAssertFindPathVariants(mappings: List<PathMapping>,
                                                          sourcePath: String,
                                                          sourcePathFun: (PathMapping) -> String,
                                                          sourceFileSeparator: Char,
                                                          destPathFun: (PathMapping) -> String,
                                                          destFileSeparator: Char,
                                                          describedAs: String,
                                                          isEqualTo: List<String>) {
      val diagnosticInfo = getDiagnosticInfo(mappings, sourcePath, sourcePathFun, sourceFileSeparator, destPathFun)
      assertThat(findPathVariants(
        mappings = mappings,
        sourcePath = sourcePath,
        sourcePathFun = sourcePathFun,
        sourceFileSeparator = sourceFileSeparator,
        destPathFun = destPathFun,
        destFileSeparator = destFileSeparator
      ))
        .describedAs("$describedAs\nDiagnostic info:\n$diagnosticInfo")
        .isEqualTo(isEqualTo)
    }

    private fun getDiagnosticInfo(mappings: List<PathMapping>,
                                  sourcePath: String,
                                  sourcePathFun: (PathMapping) -> String,
                                  sourceFileSeparator: Char,
                                  destPathFun: (PathMapping) -> String): String {
      val header = """
        |Source path: $sourcePath
        |Canonical source path: ${FileUtil.toCanonicalPath(sourcePath)}
        |""".trimMargin()
      return header + mappings.joinToString {
        val sourceBase = sourcePathFun(it)
        """
        |Mapping:
        | Source base path: $sourceBase
        | Dest base path: ${destPathFun(it)}
        | Canonical source base path: ${FileUtil.toCanonicalPath(sourceBase)}
        | Is ancestor: ${FileUtil.isAncestor(sourceBase, sourcePath, false)}
        | Relative path: ${FileUtil.getRelativePath(sourceBase, sourcePath, sourceFileSeparator)}
        | """.trimMargin()
      }
    }
  }
}