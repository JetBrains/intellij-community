// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService;
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService.NestingRule;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NestedFilesInProjectViewTest extends LightPlatformCodeInsightFixtureTestCase {

  private void doTest(@NotNull final String expected) {
    final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(getProject());
    final TestProjectTreeStructure structure = new TestProjectTreeStructure(getProject(), myFixture.getTestRootDisposable());
    final AbstractProjectViewPSIPane pane = structure.createPane();
    projectView.addProjectPane(pane);
    PlatformTestUtil.expandAll(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Project\n" +
                                     " -PsiDirectory: src\n" +
                                     expected +
                                     " External Libraries\n", true);
    projectView.removeProjectPane(pane);
  }

  private static void doTestWithCustomRules(Runnable testRunnable, NestingRule... rules) {
    final ProjectViewFileNestingService nestingService = ProjectViewFileNestingService.getInstance();

    final List<NestingRule> originalRules = new ArrayList<>(nestingService.getRules().size());
    for (NestingRule rule : nestingService.getRules()) {
      originalRules.add(new NestingRule(rule.getParentFileSuffix(), rule.getChildFileSuffix()));
    }

    try {
      nestingService.setRules(Arrays.asList(rules));
      testRunnable.run();
    }
    finally {
      nestingService.setRules(originalRules);
    }
  }


  public void testJavaAndGroovyAsChildren() {
    doTestWithCustomRules(() -> {
                            myFixture.addFileToProject("Foo.java", "public class Foo {}");
                            myFixture.addFileToProject("Foo.groovy", "");
                            myFixture.addFileToProject("Foo.txt", "");

                            doTest("  -Foo.txt\n" +
                                   "   Foo\n" +
                                   "   Foo.groovy\n");
                          },
                          new NestingRule(".txt", ".java"),
                          new NestingRule(".txt", ".groovy"));
  }

  public void testJavaAndGroovyAsParents() {
    doTestWithCustomRules(() -> {
                            myFixture.addFileToProject("Foo.java", "public class Foo {}");
                            myFixture.addFileToProject("FooImpl.java", "public class FooImpl {}");
                            myFixture.addFileToProject("Foo.groovy", "");
                            myFixture.addFileToProject("Foo.txt", "");

                            doTest("  -Foo\n" +
                                   "   Foo.txt\n" +
                                   "   FooImpl\n" +
                                   "  -Foo.groovy\n" +
                                   "   Foo.txt\n");
                          },
                          new NestingRule(".java", ".txt"),
                          new NestingRule(".groovy", ".txt"),
                          new NestingRule(".java", "Impl.java"));
  }
}
