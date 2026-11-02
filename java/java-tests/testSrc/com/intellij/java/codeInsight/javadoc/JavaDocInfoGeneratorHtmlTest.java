// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory;
import com.intellij.codeInsight.javadoc.JavaDocInfoHtmlPrinter;
import com.intellij.codeInsight.javadoc.JavaDocInfoPrinter;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.io.StringReader;
import java.util.List;
import java.util.regex.Pattern;

/// HTML variant of the javadoc info generator
public final class JavaDocInfoGeneratorHtmlTest extends JavaDocInfoGeneratorTest {
  @Override
  protected @NotNull JavaDocInfoPrinter getPrinter() {
    return new JavaDocInfoHtmlPrinter();
  }
  
  @Override
  protected @NotNull String getExpectedFileExtension() {
    return ".html";
  }
  
  @Override
  protected @NotNull String decorate(@NotNull String text) {
    return com.intellij.codeInsight.documentation.DocumentationManager.decorate(text, null, null);
  }

  @Override
  protected String replaceEnvironmentDependentContent(String html) {
    if (html == null) return null;
    var baseUrl = Pattern.compile("<base href=\"([^\"]*/)[^\"]*\">").matcher(html);
    html = html.replaceAll("href=\"(?:jar:)?file:[^\"]*/((?:external|inferred)\\.annotations)\"", "href=\"$1\"");
    if (baseUrl.find()) {
      html = html.replace("href=\"" + baseUrl.group(1), "href=\"file:///placeholder/");
    }
    return StringUtil.convertLineSeparators(html.trim())
      .replaceAll("<base href=\"[^\"]*\">", "<base href=\"placeholder\">")
      .replaceAll("[ \t]+\\n", "\n");
  }

  public void testClassTypeParamsPresentation() {
    PsiClass psiClass = getTestClass();
    PsiReferenceList extendsList = psiClass.getExtendsList();
    assertNotNull(extendsList);
    PsiJavaCodeReferenceElement referenceElement = extendsList.getReferenceElements()[0];
    PsiClass superClass = extendsList.getReferencedTypes()[0].resolve();
    String docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(superClass, referenceElement);
    assertNotNull(docInfo);
    assertFileTextEquals(UIUtil.getHtmlBodyWithoutPreWrapper(docInfo));
  }

  public void testRelativeFileLinks() {
    var context = myJavaFacade.getElementFactory().createClass("C");
    var html = new StringBuilder("""
      <html><head><base href="file:/project/src/p/C.java"></head><body>
      <a href="doc-files/diagram.png">Image</a>
      <a href='../guide.html?x=1&amp;y=2#part'>Guide</a>
      <a href="doc-files/guide.html?url=https://example.com">Guide URL</a>
      <a href="https://example.com/guide.html">External</a>
      <a href="mailto:docs@example.com">Mail</a>
      <a href="psi_element://C">Class</a>
      <a href="java/lang/String.html">String</a>
      </body></html>
      """);
    var result = new JavaDocInfoHtmlPrinter().postProcess(html, getProject(), context);
    assertNotNull(result);
    assertTrue(result, result.contains("href=\"file:/project/src/p/doc-files/diagram.png\""));
    assertTrue(result, result.contains("href='file:/project/src/guide.html?x=1&amp;y=2#part'"));
    assertTrue(result, result.contains("href=\"file:/project/src/p/doc-files/guide.html?url=https://example.com\""));
    assertTrue(result, result.contains("href=\"https://example.com/guide.html\""));
    assertTrue(result, result.contains("href=\"mailto:docs@example.com\""));
    assertTrue(result, result.contains("href=\"psi_element://C\""));
    assertTrue(result, result.contains("href=\"psi_element://java.lang.String\""));
  }

  public void testRelativeFileLinkInSourceArchive() {
    var context = myJavaFacade.getElementFactory().createClass("C");
    var html = new StringBuilder("""
      <html><head><base href="jar:file:/project/sources.jar!/p/C.java"></head><body>
      <a href="doc-files/diagram.png">Diagram</a>
      </body></html>
      """);
    var result = new JavaDocInfoHtmlPrinter().postProcess(html, getProject(), context);
    assertNotNull(result);
    assertTrue(result, result.contains("href=\"jar:file:/project/sources.jar!/p/doc-files/diagram.png\""));
  }

  public void testPackageRelativeFiles() throws Exception {
    var root = createTestProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "packageRelativeFiles");
    var packageInfo = root.findFileByRelativePath("p/package-info.java");
    assertNotNull(packageInfo);
    var psiPackage = myJavaFacade.findPackage("p");
    assertNotNull(psiPackage);
    var html = JavaDocInfoGeneratorFactory.create(getProject(), psiPackage).generateDocInfo(null);
    assertNotNull(html);
    var baseUrl = VfsUtilCore.convertToURL(packageInfo.getUrl());
    assertNotNull(baseUrl);
    assertTrue(html, html.contains("<base href=\"" + baseUrl + "\">"));
    assertTrue(html, html.contains("href=\"" + baseUrl.toExternalForm().replace("package-info.java", "doc-files/diagram.png") + "\""));

    var kit = new HTMLEditorKit();
    var htmlDocument = (HTMLDocument)kit.createDefaultDocument();
    kit.read(new StringReader(decorate(html)), htmlDocument, 0);
    assertEquals(baseUrl.toExternalForm(), htmlDocument.getBase().toExternalForm());
    var image = htmlDocument.getIterator(HTML.Tag.IMG);
    assertTrue(image.isValid());
    assertEquals("doc-files/diagram.png", image.getAttributes().getAttribute(HTML.Attribute.SRC));

    var directory = PsiManager.getInstance(getProject()).findDirectory(packageInfo.getParent());
    assertNotNull(directory);
    var directoryHtml = JavaDocInfoGeneratorFactory.create(getProject(), directory).generateDocInfo(null);
    assertNotNull(directoryHtml);
    assertTrue(directoryHtml, directoryHtml.contains("<base href=\"" + baseUrl + "\">"));
  }

  public void testClassAndMemberRelativeFiles() {
    var root = createTestProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "packageRelativeFiles");
    var source = root.findFileByRelativePath("p/C.java");
    assertNotNull(source);
    var psiClass = myJavaFacade.findClass("p.C", GlobalSearchScope.projectScope(getProject()));
    assertNotNull(psiClass);
    var baseUrl = VfsUtilCore.convertToURL(source.getUrl());
    assertNotNull(baseUrl);
    var imageUrl = baseUrl.toExternalForm().replace("C.java", "doc-files/diagram.png");
    for (var element : List.of(psiClass, psiClass.getMethods()[0])) {
      var html = JavaDocInfoGeneratorFactory.create(getProject(), element).generateDocInfo(null);
      assertNotNull(html);
      assertTrue(html, html.contains("<base href=\"" + baseUrl + "\">"));
      assertTrue(html, html.contains("href=\"" + imageUrl + "\""));
    }
  }
}
