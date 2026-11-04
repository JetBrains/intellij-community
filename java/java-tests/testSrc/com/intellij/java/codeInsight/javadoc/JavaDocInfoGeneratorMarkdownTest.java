// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.javadoc.JavaDocInfoMarkdownPrinter;
import com.intellij.codeInsight.javadoc.JavaDocInfoPrinter;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/// Markdown variant of the javadoc info generator
public final class JavaDocInfoGeneratorMarkdownTest extends JavaDocInfoGeneratorTest {
  @Override
  protected @NotNull JavaDocInfoPrinter getPrinter() {
    return new JavaDocInfoMarkdownPrinter() {
      @Override
      public @NotNull StringBuilder printLinkURI(@NotNull StringBuilder builder, @NotNull PsiElement targetElement) {
        String refText = JavaDocUtil.getReferenceText(targetElement.getProject(), targetElement);
        if (refText != null) {
          return builder.append("psi_element://").append(refText
          .replace('(', ' ')
          .replace(')', ' ')
          .replace('[', ' ')
          .replace(']', ' ')
          .replace(' ', '-')
          );
        }
        return builder;
      }
    };
  }

  @Override
  protected @NotNull String getExpectedFileExtension() {
    return ".md";
  }

  @Override
  protected @NotNull String decorate(@NotNull String text) {
    return text;
  }

  @Override
  protected String replaceEnvironmentDependentContent(String html) {
    return html.replace("psi_element://", "");
  }

  public void testLinkWithoutNavigationTarget() throws Exception {
    var printer = new JavaDocInfoMarkdownPrinter() {
      @Override
      public @NotNull StringBuilder printLinkURI(@NotNull StringBuilder builder, @NotNull PsiElement targetElement) {
        return builder;
      }
    };
    var context = myJavaFacade.getElementFactory().createClass("C");
    printer.printPrologue(new StringBuilder(), URI.create("file:/project/src/p/C.java").toURL());
    var markdown = printer.printLink(new StringBuilder(), context, "Class", false);
    assertEquals("`Class`", printer.postProcess(markdown, getProject(), context));
    assertEquals("Class", printer.printLink(new StringBuilder(), context, "Class", true).toString());
  }

  public void testRelativeFileLinks() throws Exception {
    var context = myJavaFacade.getElementFactory().createClass("C");
    var printer = getPrinter();
    printer.printPrologue(new StringBuilder(), URI.create("file:/project/src/p/C.java").toURL());
    var markdown = new StringBuilder("""
      ![Image](doc-files/diagram.png)
      ![Class image](java/lang/String.html)
      [No extension](../guide)
      [Guide](../guide.html?x=1&y=2#part "Guide")
      [Guide URL](doc-files/guide.html?url=https://example.com)
      [External](https://example.com/guide.html)
      [Mail](mailto:docs@example.com)
      [Class](psi_element://C)
      [String](java/lang/String.html)
      [Anchor](#part)
      [Empty]()
      """);
    assertEquals("""
      ![Image](file:/project/src/p/doc-files/diagram.png)
      ![Class image](file:/project/src/p/java/lang/String.html)
      [No extension](file:/project/src/guide)
      [Guide](file:/project/src/guide.html?x=1&y=2#part "Guide")
      [Guide URL](file:/project/src/p/doc-files/guide.html?url=https://example.com)
      [External](https://example.com/guide.html)
      [Mail](mailto:docs@example.com)
      [Class](psi_element://C)
      [String](psi_element://java.lang.String)
      [Anchor](#part)
      [Empty]()""", printer.postProcess(markdown, getProject(), context));
  }

  public void testRelativeFileLinkInSourceArchive() throws Exception {
    var printer = getPrinter();
    printer.printPrologue(new StringBuilder(), URI.create("jar:file:/project/sources.jar!/p/C.java").toURL());
    assertEquals("![Diagram](jar:file:/project/sources.jar!/p/doc-files/diagram.png)",
                 printer.postProcess(new StringBuilder("![Diagram](doc-files/diagram.png)"), getProject(), null));
  }

  public void testRelativeLinksInCode() throws Exception {
    var printer = getPrinter();
    printer.printPrologue(new StringBuilder(), URI.create("file:/project/src/p/C.java").toURL());
    var markdown = new StringBuilder("""
      [Guide](<doc-files/guide(1).html> "Guide")
      `[Inline](doc-files/guide.html)`

      ```java
      [Block](doc-files/guide.html)
      ```
      """);
    assertEquals("""
      [Guide](<file:/project/src/p/doc-files/guide(1).html> "Guide")
      `[Inline](doc-files/guide.html)`

      ```java
      [Block](doc-files/guide.html)
      ```""", printer.postProcess(markdown, getProject(), null));
  }

  /// @implNote diff from parent: no quick doc check 
  @Override
  protected void doTestEnumConstant() {
        PsiClass psiClass = getTestClass();
        PsiField field = psiClass.getFields()[0];
        String docInfo = generateDocInfo(field);
        assertNotNull(docInfo);
        assertFileTextEquals(docInfo);
  }
}
