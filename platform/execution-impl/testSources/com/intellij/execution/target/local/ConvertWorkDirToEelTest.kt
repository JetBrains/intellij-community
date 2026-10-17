// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.local

import com.intellij.execution.ExecutionException
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class ConvertWorkDirToEelTest {
  @Test
  fun `a local path to the local eel does not change`(@TempDir workDir: Path) {
    assertThat(convertWorkDirToEel(workDir.toString(), LocalEelDescriptor)).isEqualTo(workDir)
  }

  @Test
  fun `a relative path to the local eel does not change`() {
    assertThat(convertWorkDirToEel("relative/dir", LocalEelDescriptor)).isEqualTo(Path.of("relative/dir"))
  }

  @Test
  fun `a path on a remote eel goes below the root of that eel`(@TempDir root: Path) {
    assertThat(convertWorkDirToEel("/home/user", RemotePosixEel(root))).isEqualTo(root.resolve("home").resolve("user"))
  }

  @Test
  fun `a relative path to a remote eel causes an error`(@TempDir root: Path) {
    assertThatThrownBy { convertWorkDirToEel("relative/dir", RemotePosixEel(root)) }
      .isInstanceOf(ExecutionException::class.java)
      .hasMessageContaining("relative/dir")
  }

  @Test
  fun `an incorrect path causes an error`() {
    assertThatThrownBy { convertWorkDirToEel("dir\u0000", LocalEelDescriptor) }
      .isInstanceOf(ExecutionException::class.java)
  }

  private class RemotePosixEel(override val rootPath: Path) : EelPathBoundDescriptor {
    override val name: String = "Remote Posix eel"
    override val osFamily: EelOsFamily = EelOsFamily.Posix
  }
}
