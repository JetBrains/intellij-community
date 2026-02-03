package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.MigrationTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.refactoring.migration.MigrationMapEntry

class JavaMigrationTest : MigrationTestBase() {
  fun `test package`() {
    migrationTest(JvmLanguage.JAVA, before = """
      package p1;

      import qqq.AAA;

      class C {
        AAA a = new AAA();
      }
    """.trimIndent(), after = """
      package p1;

      import jetbrains.test.AAA;

      class C {
        AAA a = new AAA();
      }
    """.trimIndent(), MigrationMapEntry("qqq", "jetbrains.test", MigrationMapEntry.PACKAGE, true)
    )
    migrationTest(JvmLanguage.JAVA, before = """
      package p1;

      import qqq.*;

      class C {
        qqq.AAA a = new qqq.AAA();
      }
    """.trimIndent(), after = """
      package p1;

      class C {
        java.lang.AAA a = new java.lang.AAA();
      }
    """.trimIndent(), MigrationMapEntry("qqq", "java.lang", MigrationMapEntry.PACKAGE, true)
    )
  }

  fun `test package migration with non existing package`() {
    migrationTest(JvmLanguage.JAVA, before = """
      package p1;
      
      import qqq.AAA;
      
      class C {
        AAA a = new AAA();
      }
    """.trimIndent(), after = """
      package p1;
      
      import zzz.bbb.AAA;
      
      class C {
        AAA a = new AAA();
      }
    """.trimIndent(), MigrationMapEntry("qqq", "zzz.bbb", MigrationMapEntry.PACKAGE, true)
    )
    migrationTest(JvmLanguage.JAVA, before = """
      package p1;
      
      import qqq.*;
      
      class C {
        qqq.AAA a = new qqq.AAA();
      }
    """.trimIndent(), after = """
      package p1;
      
      import zzz.bbb.*;
      
      class C {
        zzz.bbb.AAA a = new zzz.bbb.AAA();
      }
    """.trimIndent(), MigrationMapEntry("qqq", "zzz.bbb", MigrationMapEntry.PACKAGE, true)
    )
  }

  fun `test two classes`() {
    migrationTest(JvmLanguage.JAVA, before = """
      class A {}
      class A1 {}
      
      class B {}
      class B1 {}
      
      public class Test {
        A a;
        B b;
      }
    """.trimIndent(), after = """
      class A {}
      class A1 {}
      
      class B {}
      class B1 {}
      
      public class Test {
        A1 a;
        B1 b;
      }
    """.trimIndent(),
                  MigrationMapEntry("A", "A1", MigrationMapEntry.CLASS, true),
                  MigrationMapEntry("B", "B1", MigrationMapEntry.CLASS, true)
    )
  }

  fun `test two non existent classes`() {
    migrationTest(JvmLanguage.JAVA, before = """
      package p1;

      import qqq.aaa.XXX;

      class C {
        XXX you = new XXX();
      }
    """.trimIndent(), after = """
      package p1;

      import zzz.bbb.QQQ;

      class C {
        QQQ you = new QQQ();
      }
    """.trimIndent(), MigrationMapEntry("qqq.aaa.XXX", "zzz.bbb.QQQ", MigrationMapEntry.CLASS, false)
    )
    migrationTest(JvmLanguage.JAVA, before = """
      package p1;
      
      import qqq.aaa.*;
      
      class C1 {
        XXX you = new XXX();
      }
    """.trimIndent(), after = """
      package p1;
      
      import qqq.aaa.*;
      import zzz.bbb.QQQ;
      
      class C1 {
        QQQ you = new QQQ();
      }
    """.trimIndent(), MigrationMapEntry("qqq.aaa.XXX", "zzz.bbb.QQQ", MigrationMapEntry.CLASS, false)
    )
  }

  fun `test non existing class and non existing package`() {
    migrationTest(JvmLanguage.JAVA, before = """
      package p1;

      import qqq.aaa.XXX;

      class C {
        XXX you = new XXX();
      }
    """.trimIndent(), after = """
      package p1;

      import java.lang.String;

      class C {
        String you = new String();
      }
    """.trimIndent(), MigrationMapEntry("qqq.aaa.XXX", "java.lang.String", MigrationMapEntry.CLASS, false)
    )
    migrationTest(JvmLanguage.JAVA, before = """
      package p1;
      
      import qqq.aaa.*;
      
      class C1 {
        XXX you = new XXX();
      }
    """.trimIndent(), after = """
      package p1;
      
      import qqq.aaa.*;
      
      class C1 {
        String you = new String();
      }
    """.trimIndent(), MigrationMapEntry("qqq.aaa.XXX", "java.lang.String", MigrationMapEntry.CLASS, false)
    )
  }

  fun `test same short name class`() {
    migrationTest(JvmLanguage.JAVA, before = """
      import aaa.*;
      
      public class C {
        @Test
        void foo(){}
      
        @Test
        void bar(){}
      }
    """.trimIndent(), after = """
      import aaa.*;
      import bbb.Test;
      
      public class C {
        @Test
        void foo(){}
      
        @Test
        void bar(){}
      }
    """.trimIndent(), MigrationMapEntry("aaa.Test", "bbb.Test", MigrationMapEntry.CLASS, false)
    )
    migrationTest(JvmLanguage.JAVA, before = """
      import aaa.Test;
      
      public class C1 {
        @Test
        void foo(){}
      
        @Test
        void bar(){}
      }
    """.trimIndent(), after = """
      import bbb.Test;
      
      public class C1 {
        @Test
        void foo(){}
      
        @Test
        void bar(){}
      }
    """.trimIndent(), MigrationMapEntry("aaa.Test", "bbb.Test", MigrationMapEntry.CLASS, false)
    )
  }
}