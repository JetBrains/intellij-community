// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.ElementPresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("AT-4013")
@TestApplication
public class ElementPresentationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private PsiClass OBJECT_CLASS;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    OBJECT_CLASS = ReadAction.computeBlocking(
      () -> JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project)));
  }

  @Test
  public void testPackage() {
    ReadAction.runBlocking(() -> {
      PsiPackage javaLang = JavaDirectoryService.getInstance().getPackage(OBJECT_CLASS.getContainingFile().getContainingDirectory());
      ElementPresentation presentation = ElementPresentation.forElement(javaLang);
      assertEquals("java.lang", presentation.getQualifiedName());
      assertEquals("java.lang", presentation.getName());
      assertEquals("", presentation.getComment());
      assertSame(ElementPresentation.Noun.PACKAGE, presentation.getKind());

      PsiPackage defaultPackage = javaLang.getParentPackage().getParentPackage();
      presentation = ElementPresentation.forElement(defaultPackage);
      assertEquals("<default>", presentation.getQualifiedName());
      assertEquals("<default>", presentation.getName());
      assertEquals("", presentation.getComment());
    });
  }

  @Test
  public void testDirectory() {
    ReadAction.runBlocking(() -> {
      PsiDirectory javaLang = OBJECT_CLASS.getContainingFile().getContainingDirectory();
      ElementPresentation presentation = ElementPresentation.forElement(javaLang);
      checkFSPresentation(presentation, "/java/lang", "lang", "/java");
      assertSame(ElementPresentation.Noun.DIRECTORY, presentation.getKind());
    });
  }

  @Test
  public void testFile() {
    ReadAction.runBlocking(() -> {
      PsiFile file = OBJECT_CLASS.getContainingFile();
      ElementPresentation presentation = ElementPresentation.forElement(file);
      checkFSPresentation(presentation, "/java/lang/Object.class", "Object.class", "/java/lang");
      assertSame(ElementPresentation.Noun.FILE, presentation.getKind());
    });
  }

  @Test
  public void testXmlTag() throws IncorrectOperationException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      XmlTag tag = XmlElementFactory.getInstance(myFixture.getProject()).createTagFromText("<tagName a=\"b\">content</tagName>");
      ElementPresentation presentation = ElementPresentation.forElement(tag);
      assertEquals("<tagName>", presentation.getQualifiedName());
      assertEquals("<tagName>", presentation.getName());
      assertEquals("", presentation.getComment());
      assertSame(ElementPresentation.Noun.XML_TAG, presentation.getKind());
    });
  }

  @Test
  public void testClass() {
    ReadAction.runBlocking(() -> {
      ElementPresentation presentation = ElementPresentation.forElement(OBJECT_CLASS);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, presentation.getQualifiedName());
      assertEquals("Object", presentation.getName());
      assertEquals("java.lang", presentation.getComment());
    });
  }

  private static void checkFSPresentation(ElementPresentation presentation, String fqEnd, String name, String commentEnd) {
    fqEnd = fqEnd.replace('/', File.separatorChar);
    commentEnd = commentEnd.replace('/', File.separatorChar);
    String qualifiedName = presentation.getQualifiedName();
    assertTrue(qualifiedName.endsWith(fqEnd), qualifiedName + ".endsWith(" + fqEnd + ")");
    assertEquals(name, presentation.getName());
    String comment = presentation.getComment();
    assertTrue(comment.endsWith(commentEnd), comment + ".endsWith(" + commentEnd + ")");
  }
}
