// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService;
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService.NestingRule;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class NestedFilesInProjectViewTest extends BasePlatformTestCase {
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull List<NestingRule> rules,
                            boolean showMembers,
                            @NotNull String expectedTree) {
    ProjectViewFileNestingService nestingService = ProjectViewFileNestingService.getInstance();
    List<NestingRule> originalRules = new ArrayList<>(nestingService.getRules());
    try {
      nestingService.setRules(rules);
      checkProjectView(fixture, showMembers, expectedTree);
    }
    finally {
      nestingService.setRules(originalRules);
    }
  }

  private static void checkProjectView(@NotNull CodeInsightTestFixture fixture, boolean showMembers, @NotNull String expectedTree) {
    ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(fixture.getProject());
    TestProjectTreeStructure structure = new TestProjectTreeStructure(fixture.getProject(), fixture.getTestRootDisposable());
    structure.setShowMembers(showMembers);
    AbstractProjectViewPane pane = structure.createPane();
    projectView.addProjectPane(pane);
    PlatformTestUtil.expandAll(pane.getTree());
    PlatformTestUtil.assertTreeEqual(pane.getTree(),
                                     "-Project\n" +
                                     " -PsiDirectory: src\n" +
                                     expectedTree +
                                     " External Libraries\n", true);
    projectView.removeProjectPane(pane);
  }

  public void testJavaAndGroovyAsChildren() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}");
    myFixture.addFileToProject("Foo.groovy", "");
    myFixture.addFileToProject("Foo.txt", "");
    doTest(myFixture,
           List.of(new NestingRule(".txt", ".java"),
                   new NestingRule(".txt", ".groovy")),
           false,
           """
               -Foo.txt
                Foo
                Foo.groovy
             """);
  }

  public void testJavaAndGroovyAsParents() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}");
    myFixture.addFileToProject("FooImpl.java", "public class FooImpl {}");
    myFixture.addFileToProject("Foo.groovy", "");
    myFixture.addFileToProject("Foo.txt", "");
    doTest(myFixture,
           List.of(new NestingRule(".java", ".txt"),
                   new NestingRule(".groovy", ".txt"),
                   new NestingRule(".java", "Impl.java")),
           false,
           """
               -Foo
                Foo.txt
                FooImpl
               -Foo.groovy
                Foo.txt
             """);
  }

  public void testWithMembers() {
    myFixture.addFileToProject("Foo.java", "public class Foo {int foo;}");
    myFixture.addFileToProject("Bar.java", "class Baz {int baz;}");
    myFixture.addFileToProject("Foo.txt", "");
    myFixture.addFileToProject("Bar.txt", "");
    doTest(myFixture,
           List.of(new NestingRule(".java", ".txt")),
           true,
           """
               -Bar.java
                Bar.txt
                -Baz
                 baz:int
               -Foo
                Foo.txt
                foo:int
             """);
  }
}
