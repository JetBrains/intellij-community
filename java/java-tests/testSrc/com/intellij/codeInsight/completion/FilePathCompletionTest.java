package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.File;

public class FilePathCompletionTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    String otherRoot = myFixture.getTempDirPath() + "/otherRoot";
    assertTrue(new File(otherRoot).mkdirs());
    moduleBuilder.addSourceContentRoot(otherRoot);
  }

  public void testCompletion() {
    myFixture.addFileToProject("x/XXX.java", "");
    myFixture.addFileToProject("otherRoot/x/XXX.java", "");
    myFixture.addFileToProject("otherRoot/x/YYY.png", "");

    PsiFile file = myFixture.addFileToProject("x/C.java", "class C { String s = \"/x/<caret>\" }");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "C.java", "XXX.java", "YYY.png");
  }

  public void testOverridesWordCompletion() {
    myFixture.addFileToProject("/foo/bar.xml", "");
    myFixture.addFileToProject("/foo/boo.xml", "");
    myFixture.configureByText("a.java", """
      class A {
        String s1 = "foo/bar.xml";
        String s2 = "/foo/b<caret>";
      }""");
    assertNotNull(myFixture.completeBasic());
    assertOrderedEquals(myFixture.getLookupElementStrings(), "bar.xml", "boo.xml");
  }

  public void testShorteningLookup() {
    myFixture.addFileToProject("/aoo/bar.xml", "");
    myFixture.addFileToProject("/foo/boo.xml", "");
    myFixture.configureByText("a.java", "class A {  String s1 = \"/<caret>\"; }");
    myFixture.completeBasic();
    assertContainsElements(myFixture.getLookupElementStrings(), "foo");
    myFixture.type("f");
    assertContainsElements(myFixture.getLookupElementStrings(), "foo");
  }
}
