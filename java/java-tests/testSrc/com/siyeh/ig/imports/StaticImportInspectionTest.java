// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.imports;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class StaticImportInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/imports/static_import");
  }

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    StaticImportInspection tool = new StaticImportInspection();
    tool.allowedClasses.add("java.util.Map");
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testMethodAllowed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    StaticImportInspection tool = new StaticImportInspection();
    tool.ignoreSingeMethodImports = true;
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testReportNothingOnUnresolvedImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new StaticImportInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testReplaceNonStaticImportPreservesTrailingComment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new StaticImportInspection());
    myFixture.configureByText("package-info.java",
                              """
                                @Internal
                                package com.example;

                                import static org.jetbrains.annotations.ApiStatus.<caret>*;

                                //trailing comment
                                """);
    myFixture.launchAction(myFixture.findSingleIntention("Replace with non-static import"));
    ReadAction.runBlocking(() -> {
      PsiComment comment = ContainerUtil.find(PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiComment.class),
                                              c -> c.getText().contains("trailing comment"));
      assertNotNull(comment, "Trailing comment should still exist as a comment, but file is now:\n" + myFixture.getFile().getText());
      assertNull(PsiTreeUtil.getParentOfType(comment, PsiImportList.class),
                 "Trailing comment got absorbed into the import list, file is now:\n" + myFixture.getFile().getText());
    });
  }

  @Test
  public void testReplaceNonStaticImportKeepsCommentsBeforeClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new StaticImportInspection());
    myFixture.configureByText("MyMath.java",
                              """
                                import static java.lang.Math.<caret>PI;

                                // one
                                // two

                                public class MyMath {
                                    static void main() {
                                        System.out.println(PI);
                                    }
                                }
                                """);
    myFixture.launchAction(myFixture.findSingleIntention("Replace with non-static import"));
    myFixture.checkResult("""
                            // one
                            // two

                            public class MyMath {
                                static void main() {
                                    System.out.println(Math.PI);
                                }
                            }
                            """);
  }
}
