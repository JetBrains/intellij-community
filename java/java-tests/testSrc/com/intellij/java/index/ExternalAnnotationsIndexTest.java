// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index;

import com.intellij.psi.impl.java.stubs.index.ExternalAnnotationsIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.util.List;

public class ExternalAnnotationsIndexTest extends JavaCodeInsightFixtureTestCase {
  public void testSimple() {
    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo void bar()">
          <annotation name="test.Ann"/>
        </item>
        <item name="com.example.Foo">
          <annotation name="test.Ann"/>
        </item>
        <item name="com.example.Foo myField">
          <annotation name="test.Other"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("test.Ann", scope);
    assertSameElements(items, "com.example.Foo void bar()", "com.example.Foo");

    List<String> otherItems = ExternalAnnotationsIndex.getItemsByAnnotation("test.Other", scope);
    assertSameElements(otherItems, "com.example.Foo myField");
  }

  public void testMultipleAnnotationsXmls() {
    myFixture.addFileToProject("pkg1/annotations.xml", """
      <root>
        <item name="com.example.Foo">
          <annotation name="org.jetbrains.annotations.NotNull"/>
        </item>
      </root>
      """);

    myFixture.addFileToProject("pkg2/annotations.xml", """
      <root>
        <item name="com.example.Bar">
          <annotation name="org.jetbrains.annotations.Nullable"/>
          <annotation name="org.jetbrains.annotations.Contract">
            <val name="value" val="&quot;null -&gt; null&quot;"/>
          </annotation>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("org.jetbrains.annotations.NotNull", scope), "com.example.Foo");
    assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("org.jetbrains.annotations.Nullable", scope), "com.example.Bar");
    assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("org.jetbrains.annotations.Contract", scope), "com.example.Bar");
  }

  public void testNonAnnotationsXmlIgnored() {
    myFixture.addFileToProject("other.xml", """
      <root>
        <item name="com.example.Foo">
          <annotation name="test.Ann"/>
        </item>
      </root>
      """);

    assertEmpty(ExternalAnnotationsIndex.getItemsByAnnotation("test.Ann", GlobalSearchScope.allScope(getProject())));
  }

  public void testEmptyAnnotationsXml() {
    myFixture.addFileToProject("annotations.xml", "<root>\n</root>");

    assertEmpty(ExternalAnnotationsIndex.getItemsByAnnotation("test.Ann", GlobalSearchScope.allScope(getProject())));
  }

  public void testAnnotationWithValChildren() {
    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo java.lang.String bar()">
          <annotation name="org.jetbrains.annotations.Contract">
            <val name="value" val="&quot;!null&quot;"/>
            <val name="pure" val="true"/>
          </annotation>
        </item>
      </root>
      """);

    List<String> items =
      ExternalAnnotationsIndex.getItemsByAnnotation("org.jetbrains.annotations.Contract", GlobalSearchScope.allScope(getProject()));
    assertSameElements(items, "com.example.Foo java.lang.String bar()");
  }

  public void testTypePathAnnotationsSkipped() {
    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo java.lang.String[] bar()">
          <annotation name="org.jetbrains.annotations.NotNull"/>
          <annotation name="org.jetbrains.annotations.NotNull" typePath="/[]"/>
        </item>
        <item name="com.example.Bar java.util.List baz()">
          <annotation name="org.jetbrains.annotations.NotNull" typePath="/0;"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("org.jetbrains.annotations.NotNull", scope);
    // First item has an element-level @NotNull, so it should appear.
    // Second item only has a type-level @NotNull (typePath), so it should NOT appear.
    assertSameElements(items, "com.example.Foo java.lang.String[] bar()");
  }

  public void testMultipleFilesAggregation() {
    myFixture.addFileToProject("pkg1/annotations.xml", """
      <root>
        <item name="pkg1.A"><annotation name="test.Ann"/></item>
      </root>
      """);
    myFixture.addFileToProject("pkg2/annotations.xml", """
      <root>
        <item name="pkg2.B"><annotation name="test.Ann"/></item>
      </root>
      """);

    List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("test.Ann", GlobalSearchScope.allScope(getProject()));
    assertSameElements(items, "pkg1.A", "pkg2.B");
  }
}
