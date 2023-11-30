/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("SuspiciousToArrayCall")
public class SuspiciousToArrayCallInspectionTest extends LightJavaInspectionTestCase {

  public void testCast() {
    doMemberTest("public void testThis(java.util.List l) {" +
                 "  final String[][] ss = (String[][]) l.toArray(new Number[l.size()]);" +
                 "}");
  }

  public void testParameterized() {
    doMemberTest("public void testThis(java.util.List<String> l) {" +
                 "  l.toArray(/*Array of type 'java.lang.String[]' expected, 'java.lang.Number[]' found*/new Number[l.size()]/**/);" +
                 "}");
  }

  public void testGenerics() {
    doTest("""
             import java.util.*;class K<T extends Integer> {
                 List<T> list = new ArrayList<>();

                 String[] m() {
                     return list.toArray(/*Array of type 'java.lang.Integer[]' expected, 'java.lang.String[]' found*/new String[list.size()]/**/);
                 }
             }""");
  }

  public void testQuestionMark() {
    doTest("""
             import java.util.List;

             class Test {
               Integer[] test(List<?> list) {
                 return list.toArray(/*Array of type 'java.lang.Object[]' expected, 'java.lang.Integer[]' found*/new Integer[0]/**/);
               }
             }""");
  }

  public void testWrongGeneric() {
    doTest("""
             import java.util.*;

             class Test {
               static class X<T> extends ArrayList<Integer> {}
               Integer[] test(X<Double> x) {
                 return x.toArray(new Integer[0]);
               }

               Double[] test2(X<Double> x) {
                 return x.toArray(/*Array of type 'java.lang.Integer[]' expected, 'java.lang.Double[]' found*/new Double[0]/**/);
               }
             }""");
  }
  
  public void testStreams() {
    doTest("""
             import java.util.stream.Stream;
             class Test {
                 static {
                     Stream.of(1.0, 2.0, 3.0).toArray(/*Array of type 'java.lang.Double[]' expected, 'java.lang.Integer[]' found*/Integer[]::new/**/);
                 }
             }""");
  }
  
  public void testStreamsParens() {
    doTest("""
             import java.util.stream.Stream;
             class Test {
                 static {
                     Stream.of(1.0, 2.0, 3.0).toArray(((/*Array of type 'java.lang.Double[]' expected, 'java.lang.Integer[]' found*/Integer[]::new/**/)));
                 }
             }""");
  }
  
  public void testStreamsIntersection() {
    doTest("""
             import java.util.stream.Stream;
             class Test {
                 static {
                     Stream.of(1, 2.0, 3.0).toArray(/*Array of type 'java.lang.Number[]' expected, 'java.lang.Integer[]' found*/Integer[]::new/**/);
                 }
             }""");
  }
  
  public void testToArrayGeneric() {
    doTest("""
             import java.util.*;
             class Test {
               static <A extends CharSequence> A[] toArray(List<CharSequence> cs) {
                 //noinspection unchecked
                 return (A[]) cs.stream().filter(Objects::nonNull).toArray(CharSequence[]::new);
               }
             }""");
  }
  
  public void testToArrayGeneric2() {
    doTest("""
             import java.util.*;
             class Test {

                 static <A extends CharSequence> A[] toArray2(List<CharSequence> cs) {
                     //noinspection unchecked
                     return (A[]) cs.toArray(new CharSequence[0]);
                 }
             }""");
  }
  
  public void testCastNonRaw() {
    doTest("""
             import java.util.*;

             class Test {
               void test() {
                 List<Foo> list = new ArrayList<>();
                 Bar[] arr2 = (Bar[])list.toArray(new Foo[0]);
               }
             }

             class Foo {}
             class Bar extends Foo {}""");
  }

  public void testTypeParameter() {
    doTest("""
             import java.util.stream.*;
             import java.util.function.*;
             abstract class AbstractStreamWrapper<T extends Cloneable> implements Stream<T> {
                 private final Stream<T> delegate;

                 AbstractStreamWrapper(Stream<T> delegate) {
                     this.delegate = delegate;
                 }

                 @Override
                 public <A> A[] toArray(IntFunction<A[]> generator) {
                     return delegate.toArray(generator);
                 }
                 public <A extends CharSequence> A[] toArray2(IntFunction<A[]> generator) {
                     return delegate.toArray(/*Array of type 'java.lang.Cloneable[]' expected, 'A[]' found*/generator/**/);
                 }
                 public <A extends Cloneable> A[] toArray3(IntFunction<A[]> generator) {
                     return delegate.toArray(generator);
                 }
             }""");
  }

  public void testStreamFilter() {
    doTest("""
             import java.util.Arrays;
             class Parent {}
             class Child extends Parent {}
             class Test {
                void test(Parent[] parent) {
                  Child[] children = Arrays.stream(parent)
                    .filter(t -> t instanceof Child)
                    .toArray(Child[]::new);
                  Child[] children2 = Arrays.stream(parent)
                    .filter(Child.class::isInstance)
                    .toArray(Child[]::new);
                  Child[] children3 = Arrays.stream(parent)
                    .filter(Child.class::isInstance)
                    .filter(c -> c.hashCode() > 0)
                    .toArray(Child[]::new);
                }
             }""");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousToArrayCallInspection();
  }
}
