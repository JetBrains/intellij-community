// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class LightResolveClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNoLoadingForStarImportedClassWhenNamedImportMatches() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl unnamedFile = (PsiFileImpl)myFixture.addFileToProject("unnamed/Bar.java", "package unnamed; public class Bar {}");
    PsiClass named = myFixture.addClass("package named; public class Bar {}");
    PsiClass foo = myFixture.addClass("""
                                        import unnamed.*;
                                        import named.Bar;
                                        class Foo extends Bar {}
                                        """);

    ReadAction.runBlocking(() -> {
      assertContentsNotLoaded(unnamedFile);
      assertEquals(named, foo.getSuperClass());
      assertContentsNotLoaded(unnamedFile);
    });
  }

  private static void assertContentsNotLoaded(PsiFileImpl unnamedFile) {
    assertNull(unnamedFile.derefStub());
    assertNull(unnamedFile.getTreeElement());
  }
}
