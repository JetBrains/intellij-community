// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

internal class WslPathParserTest {
  @ParameterizedTest
  @CsvSource(
    "//wsl$/Debian/foo,         //wsl$/Debian/,         Debian",
    "//wsl$//Debian//foo,       //wsl$/Debian/,         Debian",
    "///wsl$///Debian///foo,    //wsl$/Debian/,         Debian",
    "//wsl$/Debian,             //wsl$/Debian/,         Debian",
    "//wsl$//Debian,            //wsl$/Debian/,         Debian",
    "//wsl.localhost/Ubuntu/a,  //wsl.localhost/Ubuntu/, Ubuntu",
    "//wsl.localhost//Ubuntu//a, //wsl.localhost/Ubuntu/, Ubuntu",
    "//WSL.LOCALHOST/Ubuntu,    //WSL.LOCALHOST/Ubuntu/, Ubuntu",
    "\\\\wsl.localhost/Debian/usr/bin,       //wsl.localhost/Debian/, Debian",
    "\\\\wsl.localhost\\Debian\\usr\\bin,    //wsl.localhost/Debian/, Debian",
    "\\\\wsl$\\\\Debian\\\\foo,              //wsl$/Debian/,          Debian",
    "/\\wsl$/\\Debian/\\foo,                 //wsl$/Debian/,          Debian",
  )
  fun `WSL path`(sanitizedPath: String, expectedRoot: String, expectedDistributionId: String) {
    assertEquals(expectedRoot to expectedDistributionId, WslPathParser.parsePath(sanitizedPath))
  }

  @ParameterizedTest
  @ValueSource(strings = [
    "//wsl$",
    "//wsl.localhost",
    "//wslXlocalhost/Ubuntu",
    "//wsl.localhostX/Ubuntu",
    "//example.org/share/file.txt",
    "/wsl$/Debian",
    "\\wsl$\\Debian",
    "\\\\example.org\\share",
    "C:/wsl$/Debian",
    "",
  ])
  fun `not a WSL path`(sanitizedPath: String) {
    assertNull(WslPathParser.parsePath(sanitizedPath))
  }
}
