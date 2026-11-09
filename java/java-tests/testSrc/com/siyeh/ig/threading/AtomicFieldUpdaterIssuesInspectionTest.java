// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class AtomicFieldUpdaterIssuesInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new AtomicFieldUpdaterIssuesInspection(), getEnvironmentClasses());

  @Test
  public void testAllGood() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class A {" +
           "  private volatile int value = 0;" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(A.class, \"value\");" +
           "}");
  }

  @Test
  public void testStatic() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class A {" +
           "  private static volatile int value = 0;" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater((A.class), /*Field 'value' has 'static' modifier*/(\"value\")/**/);" +
           "}");
  }

  @Test
  public void testNotVolatile() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class A {" +
           "  private int value = 0;" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(A.class, /*Field 'value' does not have 'volatile' modifier*/\"value\"/**/);" +
           "}");
  }

  @Test
  public void testFieldNotFound() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class Z {" +
           "  volatile int value = 0;" +
           "}" +
           "class A extends Z {" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(A.class, /*No field named 'value' found in class 'A'*/\"value\"/**/);" +
           "}");
  }

  @Test
  public void testWrongType1() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class A {" +
           "  private volatile long value = 0;" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(A.class, /*Field 'value' does not have type 'int'*/\"value\"/**/);" +
           "}");
  }

  @Test
  public void testWrongType2() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicLongFieldUpdater;" +
           "class A {" +
           "  private volatile int value = 0;" +
           "  private static final AtomicLongFieldUpdater updater = " +
           "    AtomicLongFieldUpdater.newUpdater(A.class, /*Field 'value' does not have type 'long'*/\"value\"/**/);" +
           "}");
  }

  @Test
  public void testWrongType3() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "class A {" +
           "  private volatile int[] value = new int[]{0};" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(A.class, long[].class, /*Field 'value' does not have type 'long[]'*/\"value\"/**/);" +
           "}");
  }

  @Test
  public void testRightType() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "import java.util.RandomAccess;" +
           "class A<T extends RandomAccess> {" +
           "  private volatile T value = null;" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(A.class, RandomAccess.class, \"value\");" +
           "}");
  }

  @Test
  public void testNotAccessible1() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;" +
           "class Z {" +
           "  private volatile int value = 0;" +
           "}" +
           "class A {" +
           "  private static final AtomicIntegerFieldUpdater updater = " +
           "    AtomicIntegerFieldUpdater.newUpdater(Z.class, /*'private' field 'value' is not accessible from here*/\"value\"/**/);" +
           "}");
  }

  /**
   * private fields are not accessible at runtime even from inner classes.
   */
  @Test
  public void testNotAccessible2() {
    inspectionFixture.get().doTest("""
             import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;class Z {  private volatile int value = 0;  static class A {
                 private static final AtomicIntegerFieldUpdater updater =\s
                   AtomicIntegerFieldUpdater.newUpdater(Z.class, /*'private' field 'value' is not accessible from here*/"value"/**/);
               }}""");
  }

  @Test
  public void testNotAccessible3() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "import one.ProtectedOther;" +
           "class X {" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(ProtectedOther.class, String.class, /*'protected' field 's' is not accessible from here*/\"s\"/**/);" +
           "}");
  }

  @Test
  public void testNotAccessible4() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "import one.PackageLocalOther;" +
           "class X {" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(PackageLocalOther.class, String.class, /*Package-private field 's' is not accessible from here*/\"s\"/**/);" +
           "}");
  }

  @Test
  public void testAccessible1() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "class X {" +
           "  protected volatile String value;" +
           "  private static final AtomicReferenceFieldUpdater<X, String> X_UPDATER = AtomicReferenceFieldUpdater\n" +
           "          .newUpdater(X.class, String.class, \"value\");" +
           "}");
  }

  @Test
  public void testAccessible2() {
    inspectionFixture.get().doTest("import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "import one.ProtectedOther;" +
           "class X extends ProtectedOther {" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(ProtectedOther.class, String.class, \"s\");" +
           "}");
  }

  @Test
  public void testAccessible3() {
    inspectionFixture.get().doTest("package one;" +
           "import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;" +
           "class X {" +
           "  private static final AtomicReferenceFieldUpdater updater = " +
           "    AtomicReferenceFieldUpdater.newUpdater(PackageLocalOther.class, String.class, \"s\");" +
           "}");
  }

  @Test
  public void testAccessibleAnonymousCtor() {
    inspectionFixture.get().doTest("""
             import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

             class Test {
               private volatile int field;

               static class Holder {
                 Holder(AtomicIntegerFieldUpdater<Test> obj) {}
               }

               void test() {
                 new Holder(AtomicIntegerFieldUpdater.newUpdater(Test.class, "field"));
                 new Holder(AtomicIntegerFieldUpdater.newUpdater(Test.class, "field")) {};
               }
             }""");
  }

  @Test
  public void testAvoidNPE() {
    inspectionFixture.get().doTest("""
             import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
             class Z</*!Cyclic inheritance involving 'T'*//*!*/T extends T> {
               private T value = null;
               private static final AtomicReferenceFieldUpdater updater =\s
                   AtomicReferenceFieldUpdater.newUpdater(Z.class, Object.class, "value");
             }""");
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "package one;" +
      "public class ProtectedOther {" +
      "  protected volatile String s;" +
      "}"
      ,
      "package one;" +
      "public class PackageLocalOther {" +
      "  volatile String s;" +
      "}"
    };
  }
}