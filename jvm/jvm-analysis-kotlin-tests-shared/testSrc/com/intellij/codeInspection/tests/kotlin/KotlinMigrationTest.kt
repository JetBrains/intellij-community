package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.migrationFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.refactoring.migration.MigrationMapEntry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinMigrationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  protected val migration by migrationFixture(projectFixture)
  

  @Test
  fun `test package`(): Unit = timeoutRunBlocking {
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      package p1

      import qqq.AAA

      class C {
        val a = AAA()
      }
    """.trimIndent(), after = """
      package p1

      import jetbrains.test.AAA

      class C {
        val a = AAA()
      }
    """.trimIndent(), MigrationMapEntry("qqq", "jetbrains.test", MigrationMapEntry.PACKAGE, true)
    )
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      package p1
      
      import qqq.*
      
      class C {
        val a = qqq.AAA()
      }
    """.trimIndent(), after = """
      package p1
      
      import jetbrains.test.*
      
      class C {
        val a = jetbrains.test.AAA()
      }
    """.trimIndent(), MigrationMapEntry("qqq", "jetbrains.test", MigrationMapEntry.PACKAGE, true)
    )
  }

  @Test
  fun `test package migration with non existing package`(): Unit = timeoutRunBlocking {
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      package p1
      
      import qqq.AAA
      
      class C {
        val a = AAA()
      }
    """.trimIndent(), after = """
      package p1
      
      import zzz.bbb.AAA
      
      class C {
        val a = AAA()
      }
    """.trimIndent(), MigrationMapEntry("qqq", "zzz.bbb", MigrationMapEntry.PACKAGE, true)
    )
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      package p1
      
      import qqq.*
      
      class C {
        val a = qqq.AAA()
      }
    """.trimIndent(), after = """
      package p1
      
      import zzz.bbb.*
      
      class C {
        val a = zzz.bbb.AAA()
      }
    """.trimIndent(), MigrationMapEntry("qqq", "zzz.bbb", MigrationMapEntry.PACKAGE, true)
    )
  }

  @Test
  fun `test two classes`(): Unit = timeoutRunBlocking {
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      class A {}
      class A1 {}
      
      class B {}
      class B1 {}
      
      public class Test {
        val a: A
        val b: B
      }
    """.trimIndent(), after = """
      class A {}
      class A1 {}
      
      class B {}
      class B1 {}
      
      public class Test {
        val a: A1
        val b: B1
      }
    """.trimIndent(),
                  MigrationMapEntry("A", "A1", MigrationMapEntry.CLASS, true),
                  MigrationMapEntry("B", "B1", MigrationMapEntry.CLASS, true)
    )
  }

  @Test
  fun `test two non existent classes`(): Unit = timeoutRunBlocking {
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      package p1

      import qqq.aaa.XXX

      class C {
        val you = XXX()
      }
    """.trimIndent(), after = """
      package p1

      import zzz.bbb.QQQ

      class C {
        val you = QQQ()
      }
    """.trimIndent(), MigrationMapEntry("qqq.aaa.XXX", "zzz.bbb.QQQ", MigrationMapEntry.CLASS, false)
    )
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      package p1
      
      import qqq.aaa.*
      
      class C1 {
        val you = XXX()
      }
    """.trimIndent(), after = """
      package p1
      
      import qqq.aaa.*
      import zzz.bbb.QQQ
      
      class C1 {
        val you = QQQ()
      }
    """.trimIndent(), MigrationMapEntry("qqq.aaa.XXX", "zzz.bbb.QQQ", MigrationMapEntry.CLASS, false)
    )
  }

  @Test
  fun `test non existing class and non existing package`(): Unit = timeoutRunBlocking {
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      package p1

      import qqq.aaa.XXX

      class C {
        val you = XXX()
      }
    """.trimIndent(), after = """
      package p1

      import java.lang.String

      class C {
        val you = String()
      }
    """.trimIndent(), MigrationMapEntry("qqq.aaa.XXX", "java.lang.String", MigrationMapEntry.CLASS, false)
    )
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      package p1
      
      import qqq.aaa.*
      
      class C1 {
        val you = XXX()
      }
    """.trimIndent(), after = """
      package p1
      
      import java.lang.String
      
      class C1 {
        val you = String()
      }
    """.trimIndent(), MigrationMapEntry("qqq.aaa.XXX", "java.lang.String", MigrationMapEntry.CLASS, false)
    )
  }

  @Test
  fun `test same short name class`(): Unit = timeoutRunBlocking {
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      import aaa.*
      
      public class C {
        @Test
        fun foo() { }
      
        @Test
        fun bar() { }
      }
    """.trimIndent(), after = """
      import aaa.*
      import bbb.Test
      
      public class C {
        @Test
        fun foo() { }
      
        @Test
        fun bar() { }
      }
    """.trimIndent(), MigrationMapEntry("aaa.Test", "bbb.Test", MigrationMapEntry.CLASS, false)
    )
    migration.migrationTest(JvmLanguage.KOTLIN, before = """
      import aaa.Test
      
      public class C1 {
        @Test
        fun foo() { }
      
        @Test
        fun bar() { }
      }
    """.trimIndent(), after = """
      import bbb.Test
      
      public class C1 {
        @Test
        fun foo() { }
      
        @Test
        fun bar() { }
      }
    """.trimIndent(), MigrationMapEntry("aaa.Test", "bbb.Test", MigrationMapEntry.CLASS, false)
    )
  }
}