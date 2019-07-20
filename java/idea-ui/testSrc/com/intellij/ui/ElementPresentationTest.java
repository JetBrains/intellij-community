// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.ElementPresentation;

import java.io.File;

public class ElementPresentationTest extends LightJavaCodeInsightFixtureTestCase {
  private PsiClass OBJECT_CLASS;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    OBJECT_CLASS = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(project));
  }

  public void testPackage() {
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
  }

  public void testDirectory() {
    PsiDirectory javaLang = OBJECT_CLASS.getContainingFile().getContainingDirectory();
    ElementPresentation presentation = ElementPresentation.forElement(javaLang);
    checkFSPresentation(presentation, "/java/lang", "lang", "/java");
    assertSame(ElementPresentation.Noun.DIRECTORY, presentation.getKind());
  }

  public void testFile() {
    PsiFile file = OBJECT_CLASS.getContainingFile();
    ElementPresentation presentation = ElementPresentation.forElement(file);
    checkFSPresentation(presentation, "/java/lang/Object.class", "Object.class", "/java/lang");
    assertSame(ElementPresentation.Noun.FILE, presentation.getKind());
  }

  public void testXmlTag() throws IncorrectOperationException {
    XmlTag tag = XmlElementFactory.getInstance(getProject()).createTagFromText("<tagName a=\"b\">content</tagName>");
    ElementPresentation presentation = ElementPresentation.forElement(tag);
    assertEquals("<tagName>", presentation.getQualifiedName());
    assertEquals("<tagName>", presentation.getName());
    assertEquals("", presentation.getComment());
    assertSame(ElementPresentation.Noun.XML_TAG, presentation.getKind());
  }

  public void testClass() {
    ElementPresentation presentation = ElementPresentation.forElement(OBJECT_CLASS);
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, presentation.getQualifiedName());
    assertEquals("Object", presentation.getName());
    assertEquals("java.lang", presentation.getComment());
  }

  private static void checkFSPresentation(ElementPresentation presentation, String fqEnd, String name, String commentEnd) {
    fqEnd = fqEnd.replace('/', File.separatorChar);
    commentEnd = commentEnd.replace('/', File.separatorChar);
    String qualifiedName = presentation.getQualifiedName();
    assertTrue(qualifiedName + ".endsWith(" + fqEnd + ")", qualifiedName.endsWith(fqEnd));
    assertEquals(name, presentation.getName());
    String comment = presentation.getComment();
    assertTrue(comment + ".endsWith(" + commentEnd + ")", comment.endsWith(commentEnd));
  }
}
