/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class SharedThreadLocalRandomInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SharedThreadLocalRandomInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util.concurrent;\n" +
      "import java.util.Random;" +
      "public class ThreadLocalRandom extends Random {" +
      "  public static ThreadLocalRandom current() {" +
      "    return null;" +
      "  }" +
      "}"
    };
  }

  @Test
  public void testNoWarn() {
    inspectionFixture.get().doStatementTest("System.out.println(java.util.concurrent.ThreadLocalRandom.current().nextInt());");
  }

  @Test
  public void testArgument() {
    inspectionFixture.get().doStatementTest("System.out.println(java.util.concurrent.ThreadLocalRandom./*'ThreadLocalRandom' instance might be shared between threads*/current/**/());");
  }

  @Test
  public void testNoWarn2() {
    inspectionFixture.get().doMemberTest("void m() {" +
                 "  java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();" +
                 "}");
  }

  @Test
  public void testNestedClass() {
    inspectionFixture.get().doMemberTest("void m() {" +
                 "  java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom./*'ThreadLocalRandom' instance might be shared between threads*/current/**/();" +
                 "  class Z extends Thread {" +
                 "    public void run() {" +
                 "      System.out.println(r.nextInt(1));" +
                 "    }" +
                 "  }" +
                 "}");
  }

  @Test
  public void testAssignmentToField() {
    inspectionFixture.get().doTest("import java.util.concurrent.ThreadLocalRandom;" +
           "class A {" +
           "  private ThreadLocalRandom r;" +
           "  void m() {" +
           "    r = ThreadLocalRandom./*'ThreadLocalRandom' instance might be shared between threads*/current/**/();" +
           "  }" +
           "}");
  }

  @Test
  public void testCollectionsShuffle() {
    inspectionFixture.get().doStatementTest("java.util.Collections.shuffle(new java.util.ArrayList(), java.util.concurrent.ThreadLocalRandom.current());");
  }

  @Test
  public void testCollectionsShuffle2() {
    inspectionFixture.get().doMemberTest("void f(java.util.List l) {" +
                 "  java.util.Random r = java.util.concurrent.ThreadLocalRandom.current();" +
                 "  java.util.Collections.shuffle(l, r);" +
                 "}");
  }
}
