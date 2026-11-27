// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService;
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService.NestingRule;
import com.intellij.ide.projectView.impl.ProjectViewImpl;
import com.intellij.projectView.TestProjectTreeStructure;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class NestedFilesInProjectViewTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NotNull List<NestingRule> rules,
                            boolean showMembers,
                            @NotNull String expectedTree) {
    ProjectViewFileNestingService nestingService = ProjectViewFileNestingService.getInstance();
    List<NestingRule> originalRules = new ArrayList<>(nestingService.getRules());
    try {
      nestingService.setRules(rules);
      EdtTestUtil.runInEdtAndWait(() -> checkProjectView(fixture, showMembers, expectedTree));
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
                                     " +External Libraries\n", true);
    projectView.removeProjectPane(pane);
  }

  @Test
  public void testJavaAndGroovyAsChildren() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testJavaAndGroovyAsParents() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testWithMembers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
                 baz: int
               -Foo
                Foo.txt
                foo: int
             """);
  }
}
