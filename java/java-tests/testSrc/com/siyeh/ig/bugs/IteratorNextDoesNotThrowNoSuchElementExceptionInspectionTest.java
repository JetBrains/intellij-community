// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class IteratorNextDoesNotThrowNoSuchElementExceptionInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new IteratorNextDoesNotThrowNoSuchElementExceptionInspection());

  @Test
  public void testPrevious() {
    inspectionFixture.get().doTest("import java.util.*;" +
           "class ReverseListIterator<T> implements Iterator<T> {" +
           "    private ListIterator<T> iterator;" +
           "    public ReverseListIterator(List<T> list) {" +
           "        this.iterator = list.listIterator(list.size());" +
           "    }" +
           "    public boolean hasNext() {" +
           "        return iterator.hasPrevious();" +
           "    }" +
           "    public T next() {" +
           "        return iterator.previous();" +
           "    }" +
           "    public void remove() {" +
           "        iterator.remove();" +
           "    }" +
           "}");
  }

  @Test
  public void testEmpty() {
    inspectionFixture.get().doTest("import java.util.*;" +
           "class EmptyIterator<T> implements Iterator<T> {" +
           "    public boolean hasNext() {" +
           "        return false;" +
           "    }" +
           "    public T /*'Iterator.next()' which can't throw 'NoSuchElementException'*/next/**/() {" +
           "        return null;" +
           "    }" +
           "    public void remove() {" +
           "    }" +
           "}");
  }

  @Test
  public void testCompiledMethodCall() {
    inspectionFixture.get().doTest("import java.util.*;\n" +
           "\n" +
           "class MyIterator implements Iterator<String> {\n" +
           "  @Override\n" +
           "  public boolean hasNext() {\n" +
           "    return true;\n" +
           "  }\n" +
           "\n" +
           "  @Override\n" +
           "  public String /*'Iterator.next()' which can't throw 'NoSuchElementException'*/next/**/() {\n" +
           "    return \"xyz\".trim();\n" + // cannot analyze compiled library method
           "  }\n" +
           "  \n" +
           "  public void remove() {}\n" +
           "}\n");
  }

  @Test
  public void testEnumeration() {
    inspectionFixture.get().doTest("import java.util.*;" +
           "class EnumerationIterator<T> implements Iterator<T> {" +
           "  Enumeration<T> myEnumeration;" +
           "  EnumerationIterator(Enumeration<T> enumeration) {" +
           "    myEnumeration = enumeration;" +
           "  }" +
           "  public boolean hasNext() {" +
           "    return myEnumeration.hasMoreElements();" +
           "  }" +
           "  public T next() {" +
           "    return myEnumeration.nextElement();" +
           "  }" +
           "  public void remove() {}" +
           "}");
  }

  @Test
  public void testInsideAnonymous() {
    inspectionFixture.get().doTest("""
             import java.util.*;class A<T> {{Iterator<T> i = new Iterator<>() {  Enumeration<T> myEnumeration;  public boolean hasNext() {    return myEnumeration.hasMoreElements();  }  public T next() {    if (this.hasNext()) {
                   return null;
                 }
                 throw new NoSuchElementException();  }  public void remove() {}};}}""");
  }

  @Test
  public void testAbstractMethod() {
    inspectionFixture.get().doTest("""
             import java.util.Iterator;
             interface MyIterator extends Iterator<String> {
                 @Override
                 String next();
             }""");
  }
}