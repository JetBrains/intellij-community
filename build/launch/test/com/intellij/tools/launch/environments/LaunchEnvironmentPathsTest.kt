package com.intellij.tools.launch.environments

import com.intellij.openapi.application.PathManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class LaunchEnvironmentPathsTest {
  @Test
  fun test() {
    val basePath = Paths.get(PathManager.getHomePath())
    val thisPath = Paths.get(PathManager.getCommunityHomePath())
    assertEquals(
      "/intellij/community",
      thisPath.resolve(baseLocalPath = basePath, baseEnvPath = "/intellij", envFileSeparator = '/')
    )
    assertEquals(
      "/intellij",
      basePath.resolve(baseLocalPath = basePath, baseEnvPath = "/intellij", envFileSeparator = '/')
    )
  }
}