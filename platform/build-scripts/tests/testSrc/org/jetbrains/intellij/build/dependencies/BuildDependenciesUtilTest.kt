package org.jetbrains.intellij.build.dependencies

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class BuildDependenciesUtilTest {
  @Rule
  @JvmField val thrown = ExpectedException.none()

  @Test
  fun testEntryFile() {
    Assert.assertEquals(
      "xxx",
      BuildDependenciesUtil.normalizeEntryName("///////xxx\\")
    )
    Assert.assertEquals(
      "..xxx",
      BuildDependenciesUtil.normalizeEntryName("///////..xxx")
    )
  }

  @Test
  fun testTrim() {
    val trimChar = '/'
    Assert.assertEquals("", BuildDependenciesUtil.trim("/", trimChar))
    Assert.assertEquals("", BuildDependenciesUtil.trim("//", trimChar))
    Assert.assertEquals("", BuildDependenciesUtil.trim("", trimChar))
    Assert.assertEquals("a", BuildDependenciesUtil.trim("/a/", trimChar))
    Assert.assertEquals("xx/yy", BuildDependenciesUtil.trim("/xx/yy/", trimChar))
    Assert.assertEquals("xxx", BuildDependenciesUtil.trim("////xxx//", trimChar))
  }

  @Test
  fun testEntryFileInvalid() {
    thrown.expectMessage("Invalid entry name: ../xxx")
    BuildDependenciesUtil.normalizeEntryName("/../xxx")
  }

  @Test
  fun testEntryFileInvalid2() {
    thrown.expectMessage("Normalized entry name should not contain '//': a//xxx")
    BuildDependenciesUtil.normalizeEntryName("a/\\xxx")
  }
}
