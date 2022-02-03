package org.jetbrains.intellij.build.dependencies

import groovy.transform.CompileStatic
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

import static org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.trim

@CompileStatic
class BuildDependenciesUtilTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none()

  @Test
  void testEntryFile() {
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
  void testTrim() {
    char trimChar = '/'
    Assert.assertEquals("", trim("/", trimChar))
    Assert.assertEquals("", trim("//", trimChar))
    Assert.assertEquals("", trim("", trimChar))
    Assert.assertEquals("a", trim("/a/", trimChar))
    Assert.assertEquals("xx/yy", trim("/xx/yy/", trimChar))
    Assert.assertEquals("xxx", trim("////xxx//", trimChar))
  }

  @Test
  void testEntryFileInvalid() {
    thrown.expectMessage("Invalid entry name: ../xxx")
    BuildDependenciesUtil.normalizeEntryName("/../xxx")
  }

  @Test
  void testEntryFileInvalid2() {
    thrown.expectMessage("Normalized entry name should not contain '//': a//xxx")
    BuildDependenciesUtil.normalizeEntryName("a/\\xxx")
  }
}
