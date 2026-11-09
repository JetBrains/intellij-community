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
package com.siyeh.ig.visibility;

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
public class TypeParameterHidesVisibleTypeInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new TypeParameterHidesVisibleTypeInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("""
             import java.util.*;
             abstract class TypeParameterHidesVisibleTypeInspection<List> {
                 private Map map = new HashMap();
                 public abstract List foo();
                 public abstract </*Type parameter 'Set' hides visible type 'java.util.Set'*/Set/**/> Set bar();
                 public abstract </*Type parameter 'TypeParameterHidesVisibleTypeInspection' hides visible type 'TypeParameterHidesVisibleTypeInspection'*/TypeParameterHidesVisibleTypeInspection/**/> TypeParameterHidesVisibleTypeInspection baz();
                 public abstract <InputStream> InputStream baz3();
                 public abstract <A> A baz2();
             }
             """);
  }

  @Test
  public void testHiddenTypeParameter() {
    inspectionFixture.get().doTest("""
             import java.util.*;
              abstract class MyList<T> extends AbstractList<T> {
                 private List<T> elements;
                 public </*Type parameter 'T' hides type parameter 'T'*/T/**/> T[] toArray( T[] array ) {
                     return elements.toArray( array );
                 }
             }
             """);
  }

  @Test
  public void testCanNotHideFromStaticContext() {
    inspectionFixture.get().doTest("""
             class Y<T> {
                 static class X<T> {
                     T t;
                 }
                 static <T>  T go() {
                     return null;
                 }
             }""");
  }

  @Test
  public void testCannotHideFromImplicitStaticContext() {
    inspectionFixture.get().doTest("""
             interface G<T> {
                 class I {
                     class H<T>  {
                          T t;
                     }
                 }
             }""");
  }
}
