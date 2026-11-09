/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

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
public class ExtendsAnnotationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ExtendsAnnotationInspection(), getEnvironmentClasses());

  @Test
  public void testExtendsAnnotation() {
    inspectionFixture.get().doTest();
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package javax.enterprise.util;
import java.lang.annotation.Annotation;
public abstract class AnnotationLiteral<T extends Annotation> implements Annotation {
    protected AnnotationLiteral() {}
    public Class<? extends Annotation> annotationType() { return null; }
    @Override public boolean equals(Object other) { return false; }
    @Override public int hashCode() { return 0; }
    @Override public String toString() { return ""; }
}"""
    };
  }
}
