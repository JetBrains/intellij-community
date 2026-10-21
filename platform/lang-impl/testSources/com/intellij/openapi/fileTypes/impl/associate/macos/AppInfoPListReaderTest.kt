// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate.macos

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/** IJPL-243224: an `Info.plist` starts with a DOCTYPE declaration, and the reader must accept it. */
class AppInfoPListReaderTest {
  @Test
  fun `reads the bundle identifier from a plist with a doctype`(@TempDir dir: Path) {
    val plist = dir.resolve("Info.plist")
    plist.writeText("""
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
      <plist version="1.0">
        <dict>
          <key>CFBundleName</key>
          <string>IntelliJ IDEA</string>
          <key>CFBundleIdentifier</key>
          <string>com.jetbrains.intellij</string>
        </dict>
      </plist>
    """.trimIndent())

    val reader = AppInfoPListReader()
    reader.loadPList(plist.toFile())

    assertThat(reader.bundleIdentifier).isEqualTo("com.jetbrains.intellij")
  }
}
