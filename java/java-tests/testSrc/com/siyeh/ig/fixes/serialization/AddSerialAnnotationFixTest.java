// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.serialization;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import com.siyeh.ig.serialization.MissingSerialAnnotationInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class AddSerialAnnotationFixTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new MissingSerialAnnotationInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package java.io;
import java.lang.annotation.*;
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface Serial {}"""
    };
  }

  @Test
  public void testAdditionToField() {
    inspectionFixture.get().doTest("""
             import java.io.*;
             class Test implements Serializable {
               private static final long /*'serialVersionUID' can be annotated with '@Serial' annotation*//*_*/serialVersionUID/**/ = 7874493593505141603L;
             }""");
    inspectionFixture.get().checkQuickFix("Annotate field 'serialVersionUID' as '@Serial'", """
      import java.io.*;
      class Test implements Serializable {
        @Serial
        private static final long serialVersionUID = 7874493593505141603L;
      }""");
  }

  @Test
  public void testAdditionToMethod() {
    inspectionFixture.get().doTest("""
             import java.io.*;
             class Test implements Serializable {
               protected Object /*'readResolve()' can be annotated with '@Serial' annotation*//*_*/readResolve/**/() throws ObjectStreamException {
                 return 1;
               }
             }""");
    inspectionFixture.get().checkQuickFix("Annotate method 'readResolve()' as '@Serial'",
                  """
                    import java.io.*;
                    class Test implements Serializable {
                      @Serial
                      protected Object readResolve() throws ObjectStreamException {
                        return 1;
                      }
                    }""");
  }
}
