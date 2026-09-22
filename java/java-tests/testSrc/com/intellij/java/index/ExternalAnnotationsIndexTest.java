// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.index;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ThrowableConsumer;
import com.intellij.psi.impl.java.stubs.index.ExternalAnnotationsIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExternalAnnotationsIndexTest extends JavaCodeInsightFixtureTestCase {
  private static void withExternalAnnotations(String xmlRelativePath, String xmlContent,
                                       ThrowableConsumer<? super VirtualFile, ? extends Exception> test) throws Exception {
    Path externalAnnotationsDir = Files.createTempDirectory("extAnnotations");
    try {
      Path xmlPath = externalAnnotationsDir.resolve(xmlRelativePath);
      Files.createDirectories(xmlPath.getParent());
      Files.writeString(xmlPath, xmlContent);
      VirtualFile root = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(externalAnnotationsDir);
      assertNotNull(root);
      test.consume(root);
    }
    finally {
      NioFiles.deleteRecursively(externalAnnotationsDir);
    }
  }

  private void addLibraryAnnotationRoot(VirtualFile root) {
    WriteAction.run(() -> {
      var model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      LibraryTable libTable = model.getModuleLibraryTable();
      Library library = libTable.createLibrary("testLib");
      Library.ModifiableModel libModel = library.getModifiableModel();
      libModel.addRoot(root, AnnotationOrderRootType.getInstance());
      libModel.commit();
      model.commit();
    });
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  private void addSdkAnnotationRoot(VirtualFile root) {
    Sdk originalSdk = ModuleRootManager.getInstance(myFixture.getModule()).getSdk();
    assertNotNull(originalSdk);
    Sdk sdkWithAnnotations = PsiTestUtil.addRootsToJdk(originalSdk, AnnotationOrderRootType.getInstance(), root);
    WriteAction.run(() -> {
      ProjectJdkTable.getInstance().addJdk(sdkWithAnnotations, getTestRootDisposable());
      var model = ModuleRootManager.getInstance(myFixture.getModule()).getModifiableModel();
      model.setSdk(sdkWithAnnotations);
      model.commit();
    });
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

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
          <annotation name="test.NotNull"/>
        </item>
      </root>
      """);

    myFixture.addFileToProject("pkg2/annotations.xml", """
      <root>
        <item name="com.example.Bar">
          <annotation name="test.Nullable"/>
          <annotation name="test.Contract">
            <val name="value" val="&quot;null -&gt; null&quot;"/>
          </annotation>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("test.NotNull", scope), "com.example.Foo");
    assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("test.Nullable", scope), "com.example.Bar");
    assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("test.Contract", scope), "com.example.Bar");
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
          <annotation name="test.Contract">
            <val name="value" val="&quot;!null&quot;"/>
            <val name="pure" val="true"/>
          </annotation>
        </item>
      </root>
      """);

    List<String> items =
      ExternalAnnotationsIndex.getItemsByAnnotation("test.Contract", GlobalSearchScope.allScope(getProject()));
    assertSameElements(items, "com.example.Foo java.lang.String bar()");
  }

  public void testTypePathAnnotationsSkipped() {
    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Foo java.lang.String[] bar()">
          <annotation name="test.NotNull"/>
          <annotation name="test.NotNull" typePath="/[]"/>
        </item>
        <item name="com.example.Bar java.util.List baz()">
          <annotation name="test.NotNull" typePath="/0;"/>
        </item>
      </root>
      """);

    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("test.NotNull", scope);
    // First item has an element-level @NotNull, so it should appear.
    // Second item only has a type-level @NotNull (typePath), so it should NOT appear.
    assertSameElements(items, "com.example.Foo java.lang.String[] bar()");
  }

  public void testEntityResolutionInItemNames() {
    myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="com.example.Box &lt;T&gt;">
          <annotation name="test.Ann"/>
        </item>
        <item name="com.example.Foo java.util.Map&lt;java.lang.String, java.lang.Integer&gt; getMap()">
          <annotation name="test.Ann"/>
        </item>
      </root>
      """);

    List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("test.Ann", GlobalSearchScope.allScope(getProject()));
    assertSameElements(items, "com.example.Box <T>", "com.example.Foo java.util.Map<java.lang.String, java.lang.Integer> getMap()");
  }

  public void testExternalDoctypeIsNotResolved() {
    // Indexing must never open a stream for an external DTD: it would make indexing depend on the network
    // (see StdXMLParser.processDocType). The system id below is unreachable on purpose: if it is resolved,
    // parsing fails and nothing gets indexed.
    myFixture.addFileToProject("annotations.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE root SYSTEM "https://127.0.0.1:1/annotations.dtd">
      <root>
        <item name="com.example.Foo">
          <annotation name="test.Ann"/>
        </item>
      </root>
      """);

    List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("test.Ann", GlobalSearchScope.allScope(getProject()));
    assertSameElements(items, "com.example.Foo");
  }

  public void testExternalPublicDoctypeIsNotResolved() {
    myFixture.addFileToProject("annotations.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE root PUBLIC "-//Test//DTD Annotations//EN" "https://127.0.0.1:1/annotations.dtd">
      <root>
        <item name="com.example.Bar &lt;T&gt;">
          <annotation name="test.Ann"/>
        </item>
      </root>
      """);

    List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("test.Ann", GlobalSearchScope.allScope(getProject()));
    assertSameElements(items, "com.example.Bar <T>");
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

  public void testAnnotationRootIsIndexed() throws Exception {
    withExternalAnnotations("com/example/annotations.xml", """
      <root>
        <item name="com.example.Foo">
          <annotation name="test.LibAnn"/>
        </item>
        <item name="com.example.Foo void bar()">
          <annotation name="test.LibAnn"/>
        </item>
      </root>
      """, annRoot -> {
      addLibraryAnnotationRoot(annRoot);

      List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("test.LibAnn", GlobalSearchScope.allScope(getProject()));
      assertSameElements(items, "com.example.Foo", "com.example.Foo void bar()");
    });
  }

  public void testAnnotationRootIndexUpdatesOnXmlChange() throws Exception {
    withExternalAnnotations("com/example/annotations.xml", """
      <root>
        <item name="com.example.Foo">
          <annotation name="test.Upd"/>
        </item>
      </root>
      """, annRoot -> {
      addLibraryAnnotationRoot(annRoot);

      GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
      assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("test.Upd", scope), "com.example.Foo");

      // Modify the XML: add a new item, remove the old one
      VirtualFile xmlFile = annRoot.findFileByRelativePath("com/example/annotations.xml");
      assertNotNull(xmlFile);
      WriteAction.run(() -> xmlFile.setBinaryContent("""
        <root>
          <item name="com.example.Bar">
            <annotation name="test.Upd"/>
          </item>
          <item name="com.example.Baz">
            <annotation name="test.Upd2"/>
          </item>
        </root>
        """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());

      assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("test.Upd", scope), "com.example.Bar");
      assertSameElements(ExternalAnnotationsIndex.getItemsByAnnotation("test.Upd2", scope), "com.example.Baz");
    });
  }

  public void testSdkAnnotationRootIsIndexed() throws Exception {
    withExternalAnnotations("annotations.xml", """
      <root>
        <item name="java.lang.String">
          <annotation name="test.SdkAnn"/>
        </item>
        <item name="java.lang.String int length()">
          <annotation name="test.SdkAnn"/>
        </item>
      </root>
      """, annRoot -> {
      addSdkAnnotationRoot(annRoot);

      List<String> items = ExternalAnnotationsIndex.getItemsByAnnotation("test.SdkAnn", GlobalSearchScope.allScope(getProject()));
      assertSameElements(items, "java.lang.String", "java.lang.String int length()");
    });
  }
}
