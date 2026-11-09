// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

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
public class UnnecessaryInitCauseInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessaryInitCauseInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public\n" +
      "class MissingResourceException extends RuntimeException {" +
      "  public MissingResourceException(String s, String className, String key) {}" +
      "  MissingResourceException(String message, String className, String key, Throwable cause) {}" +
      "}"
    };
  }

  @Test
  public void testSplitDeclarationAssignment() {
    inspectionFixture.get().doMemberTest("""
                   void foo() {
                        RuntimeException exception = null;
                        try {
                            new java.io.FileInputStream("asdf");
                        } catch (java.io.FileNotFoundException e) {
                            exception = new RuntimeException();
                            exception./*Unnecessary 'Throwable.initCause()' call*/initCause/**/(e);
                        } catch (RuntimeException e) {
                            exception = e;
                        }
                        throw exception;
                   }""");
  }

  @Test
  public void testReassigned() {
    inspectionFixture.get().doMemberTest("""
                   void foo() {
                       try {
                           new java.io.FileInputStream("asdf");
                       } catch (java.io.FileNotFoundException e) {
                           RuntimeException exception = new RuntimeException();
                           e = null;
                           exception.initCause(e);
                           throw exception;
                       }
                   }""");
  }

  @Test
  public void testIncompatibleType() {
    //noinspection EmptyTryBlock
    inspectionFixture.get().doTest("import java.io.*;" +
           "class X {" +
           "  void m() throws Exception {" +
           "    try {" +
           "    }catch (RuntimeException ex) {" +
           "       YException wrapper = new YException(\"foo\");" +
           "       wrapper.initCause(ex);" +
           "       throw wrapper;" +
           "    }" +
           "  }" +
           "  class YException extends Exception {" +
           "    public YException(String msg) { super(msg); }" +
           "    public YException(String msg, IOException cause) { super(msg, cause); }" +
           "  }" +
           "}");
  }

  @Test
  @SuppressWarnings("ThrowableNotThrown")
  public void testNotAccessible() {
    inspectionFixture.get().doTest("""
             import java.util.*;class X {  static void z() {    RuntimeException cause = new RuntimeException();
                 MissingResourceException e = new MissingResourceException("asdf", "asdf", "asdf");
                 e.initCause(cause);  }}""");
  }
}