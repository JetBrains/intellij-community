// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import org.junit.Assert
import org.junit.Test

abstract class EnvVariablesTest {
  abstract fun parseEnvsFromText(text: String): Map<String, String>

  @Test
  fun basicParseTests() {
    Assert.assertEquals(3, parseEnvsFromText("t1=val1;t2=val2;t3=val3;").size.toLong())
    Assert.assertEquals(4, parseEnvsFromText("t1=val1;t2=val2;t3=val3;;empty=;").size.toLong())
    Assert.assertEquals(3, parseEnvsFromText("t1=val1;t2=val2;t3=val3;;").size.toLong())
    Assert.assertEquals(3, parseEnvsFromText("t1=val1;t2=val2;t3=val3;;;").size.toLong())
    Assert.assertEquals(3, parseEnvsFromText("t1=val1;t2=val2;t3=val3;;noise;").size.toLong())
    Assert.assertEquals(3, parseEnvsFromText("t1=val1;t2=val2;t3=val3").size.toLong())

    val map: Map<String, String> = parseEnvsFromText("t1=val1;t2=val2;t3=\"val\\;3\"")
    Assert.assertEquals(3, map.size.toLong())
    Assert.assertEquals("val;3", map["t3"])

    Assert.assertEquals(0, parseEnvsFromText("test").size.toLong())
    Assert.assertEquals(1, parseEnvsFromText("test=test").size.toLong())
  }

  @Test // IJPL-200754: Run/Dialog environment variables with semicolon values are broken when pasted in 2025.2
  fun semicolonRoundtrip() {
    val data = HashMap<String, String>()
    data["var1"] = "value1"
    data["var2"] = "value2;value2"
    assertEnvVarTableRoundtrip(data)
  }

  @Test // IJPL-188671: Environment variables editor breaks environment if value end with backslash
  fun backslashRoundtrip() {
    val data = HashMap<String, String>()
    data["var1"] = "value1\\"
    data["var2"] = "value2"
    assertEnvVarTableRoundtrip(data)
  }

  @Test
  fun escapingTests() {
    var map: Map<String, String> = parseEnvsFromText("key=\"value\"blah;")
    Assert.assertEquals(1, map.size.toLong())
    Assert.assertEquals("valueblah", map["key"])

    map = parseEnvsFromText("key=\"value\"\"blah\";")
    Assert.assertEquals(1, map.size.toLong())
    Assert.assertEquals("value\"blah\"", map["key"])

    map = parseEnvsFromText("key=\"value\\")
    Assert.assertEquals(1, map.size.toLong())
    Assert.assertEquals("value\\", map["key"])

    map = parseEnvsFromText("key=\"value;")
    Assert.assertEquals(1, map.size.toLong())
    Assert.assertEquals("value;", map["key"])

    map = parseEnvsFromText("key={\"key\" : \"value\"}")
    Assert.assertEquals(1, map.size.toLong())
    Assert.assertEquals("{\"key\" : \"value\"}", map["key"])

    map = parseEnvsFromText("key={\"key\" : \"val;ue\"}")
    Assert.assertEquals(1, map.size.toLong())
    Assert.assertEquals("{\"key\" : \"val", map["key"])

    map = parseEnvsFromText("t1=val1;t2=val2;t3=val\\;3")
    Assert.assertEquals(3, map.size.toLong())
    Assert.assertEquals("val\\", map["t3"])

    Assert.assertEquals(1, parseEnvsFromText("test=test").size.toLong())

    map = parseEnvsFromText("var1=ffff;var2=C:\\CRM\\files\\;\"va\\\"=;r3\"=aaaa;var4=\"C:\\\\CRM\\\\files\\\\\"")
    Assert.assertEquals(4, map.size.toLong())
    Assert.assertEquals("ffff", map["var1"])
    Assert.assertEquals("C:\\CRM\\files\\", map["var2"])
    Assert.assertEquals("C:\\CRM\\files\\", map["var4"])
    Assert.assertEquals("aaaa", map["va\"=;r3"])
  }

  @Test
  fun newLineSeparatedEnvVars() {
    Assert.assertEquals(3, parseEnvsFromText("t1=val1\nt2=val2\nt3=val3\n").size.toLong())
    Assert.assertEquals(4, parseEnvsFromText("t1=val1\nt2=val2\nt3=val3\n\nempty=\n").size.toLong())
    Assert.assertEquals(3, parseEnvsFromText("t1=val1\nt2=val2\nt3=val3\n\n").size.toLong())
    Assert.assertEquals(3, parseEnvsFromText("t1=val1\nt2=val2\nt3=val3\n\n\n").size.toLong())
    Assert.assertEquals(3, parseEnvsFromText("t1=val1\nt2=val2\nt3=val3\n\nnoise\n").size.toLong())
    Assert.assertEquals(3, parseEnvsFromText("t1=val1\nt2=val2\nt3=val3").size.toLong())
  }

  private fun assertEnvVarTableRoundtrip(data: MutableMap<String, String>) {
    val stringification = EnvironmentVariablesTextFieldWithBrowseButton.stringifyEnvironment(
      EnvironmentVariablesData.create(data, false, null)
    )
    val parsed: Map<String, String> = parseEnvsFromText(stringification)
    Assert.assertEquals(data, parsed)
  }
}

class EnvVariablesTableTest : EnvVariablesTest() {
  override fun parseEnvsFromText(text: String): Map<String, String> = EnvVariablesTable.parseEnvsFromText(text)
}

class EnvVariablesParserTest : EnvVariablesTest() {
  override fun parseEnvsFromText(text: String): Map<String, String> = EnvVariables.parseFromText(text).envs

  @Test
  fun testEnvFileParsing() {
    var parsed = EnvVariables.parseFromText("t1=val1;t2=val2;t3=val3;;noise;C:\\SomeRandomFolder\\file.env;in<va>lid:p*th")
    Assert.assertEquals(3, parsed.envs.size.toLong())
    Assert.assertEquals("noise", parsed.envFiles[0])
    Assert.assertEquals("C:\\SomeRandomFolder\\file.env", parsed.envFiles[1])
    Assert.assertEquals("in<va>lid:p*th", parsed.envFiles[2])

    parsed = EnvVariables.parseFromText("t1=val1;t2=val2;t3=val3;;noise;C:\\SomeRandomFolder\\file.env;key=p*th")
    Assert.assertEquals(6, parsed.envs.size.toLong())
    Assert.assertEquals("p*th", parsed.envs["key"])
    Assert.assertEquals("", parsed.envs["noise"])
    Assert.assertEquals("", parsed.envs["C:\\SomeRandomFolder\\file.env"])
    Assert.assertEquals(0, parsed.envFiles.size)
  }
}

