// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.imports;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class StaticImportInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/imports/static_import";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testSimple() {
    StaticImportInspection tool = new StaticImportInspection();
    tool.allowedClasses.add("java.util.Map");
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testMethodAllowed() {
    StaticImportInspection tool = new StaticImportInspection();
    tool.ignoreSingeMethodImports = true;
    myFixture.enableInspections(tool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testReportNothingOnUnresolvedImport() {
    myFixture.enableInspections(new StaticImportInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testReplaceNonStaticImportPreservesTrailingComment() {
    myFixture.enableInspections(new StaticImportInspection());
    myFixture.configureByText("package-info.java",
                              """
                                @Internal
                                package com.example;

                                import static org.jetbrains.annotations.ApiStatus.<caret>*;

                                //trailing comment
                                """);
    myFixture.launchAction(myFixture.findSingleIntention("Replace with non-static import"));
    PsiComment comment = ContainerUtil.find(PsiTreeUtil.findChildrenOfType(myFixture.getFile(), PsiComment.class),
                                            c -> c.getText().contains("trailing comment"));
    assertNotNull("Trailing comment should still exist as a comment, but file is now:\n" + myFixture.getFile().getText(), comment);
    assertNull("Trailing comment got absorbed into the import list, file is now:\n" + myFixture.getFile().getText(),
               PsiTreeUtil.getParentOfType(comment, PsiImportList.class));
  }

  public void testReplaceNonStaticImportKeepsCommentsBeforeClass() {
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
