// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class LightResolveClassTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNoLoadingForStarImportedClassWhenNamedImportMatches() {
    PsiFileImpl unnamedFile = (PsiFileImpl)myFixture.addFileToProject("unnamed/Bar.java", "package unnamed; public class Bar {}");
    PsiClass named = myFixture.addClass("package named; public class Bar {}");
    PsiClass foo = myFixture.addClass("""
                                        import unnamed.*;
                                        import named.Bar;
                                        class Foo extends Bar {}
                                        """);

    assertContentsNotLoaded(unnamedFile);
    assertEquals(named, foo.getSuperClass());
    assertContentsNotLoaded(unnamedFile);
  }

  private static void assertContentsNotLoaded(PsiFileImpl unnamedFile) {
    assertNull(unnamedFile.derefStub());
    assertNull(unnamedFile.getTreeElement());
  }
}
