// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassListReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class JavaClassReferenceSetTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNoDuplicates() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<String> rootNames = ReadAction.computeBlocking(() -> new ArrayList<>(JavaClassReferenceProvider.getDefaultPackagesNames(myFixture.getProject())));
    myFixture.configureByText(JavaFileType.INSTANCE, "class Test {\n" +
                                                     "    String foo = \"<caret>"+rootNames.get(0)+"."+rootNames.get(1)+"."+rootNames.get(2)+".XXXX13\";\n" +
                                                     "}");
    ReadAction.runBlocking(() -> {
      PsiLiteralExpression element = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiLiteralExpression.class);
      String text = ElementManipulators.getValueText(element);

      PsiReference[] fromProvider = new JavaClassListReferenceProvider().getReferencesByString(text, element, 1);
      assertThat(fromProvider).hasSize(4);

      JavaClassReferenceSet referenceSet = new JavaClassReferenceSet(text, element, 1, false, new JavaClassReferenceProvider());
      PsiReference[] references = referenceSet.getReferences();
      assertThat(references).hasSize(4);
    });
  }

  @Test
  public void testResolveClassInDeclaredPackage() {
    addMyService();
    PsiReference[] references = createReferences("acme.corp.tools.MyService");
    ReadAction.runBlocking(() -> {
      assertThat(references).hasSize(4);

      assertInstanceOf(PsiPackage.class, references[0].resolve());
      assertInstanceOf(PsiPackage.class, references[1].resolve());
      assertInstanceOf(PsiPackage.class, references[2].resolve());
      assertInstanceOf(PsiClass.class, references[3].resolve());
    });
  }

  @Test
  public void testUnknownSubpackageOfDeclaredPackageIsUnresolved() {
    addMyService();
    PsiReference[] references = createReferences("acme.nope.Missing");
    ReadAction.runBlocking(() -> {
      assertThat(references).hasSize(3);

      assertInstanceOf(PsiPackage.class, references[0].resolve());
      assertNull(references[1].resolve());
      assertNull(references[2].resolve());
    });
  }

  private void addMyService() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("MyService.java", """
      package acme.corp.tools;

      public class MyService { }
      """);
  }

  private PsiReference[] createReferences(String qualifiedName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, "class Test {\n" +
                                                     "    String foo = \"<caret>" + qualifiedName + "\";\n" +
                                                     "}");
    return ReadAction.computeBlocking(() -> {
      PsiLiteralExpression element = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiLiteralExpression.class);
      String text = ElementManipulators.getValueText(element);
      return new JavaClassReferenceSet(text, element, 1, false, new JavaClassReferenceProvider()).getReferences();
    });
  }
}
