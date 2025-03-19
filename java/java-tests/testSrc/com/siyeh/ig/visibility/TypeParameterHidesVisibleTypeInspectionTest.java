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

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class TypeParameterHidesVisibleTypeInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("""
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

  public void testHiddenTypeParameter() {
    doTest("""
             import java.util.*;
              abstract class MyList<T> extends AbstractList<T> {
                 private List<T> elements;
                 public </*Type parameter 'T' hides type parameter 'T'*/T/**/> T[] toArray( T[] array ) {
                     return elements.toArray( array );
                 }
             }
             """);
  }

  public void testCanNotHideFromStaticContext() {
    doTest("""
             class Y<T> {
                 static class X<T> {
                     T t;
                 }
                 static <T>  T go() {
                     return null;
                 }
             }""");
  }

  public void testCannotHideFromImplicitStaticContext() {
    doTest("""
             interface G<T> {
                 class I {
                     class H<T>  {
                          T t;
                     }
                 }
             }""");
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new TypeParameterHidesVisibleTypeInspection();
  }
}
