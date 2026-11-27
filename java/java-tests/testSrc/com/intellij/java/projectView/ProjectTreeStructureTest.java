// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectView.ProjectViewFixture;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static com.intellij.projectView.ProjectViewFixture.checkContainsMethod;
import static com.intellij.projectView.ProjectViewFixture.projectViewFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ProjectTreeStructureTest {
  private final TestFixture<ProjectViewFixture> projectViewFixture = projectViewFixture(new Queryable.PrintInfo());

  @Test
  public void test1() {
    ProjectViewFixture fixture = projectViewFixture.get();
    fixture.getStructure().setProviders(new SameNamesJoiner(), new ClassNameConvertor(fixture.getProject()));
    fixture.assertStructureEqual(fixture.getPackageDirectory(), """
      package1
       Class2.java converted
       Form1 joined
        Form1.form
        Form1.java converted
       Form2.form
      """);
  }

  @Test
  public void testStandardProviders() {
    ProjectViewFixture fixture = projectViewFixture.get();
    fixture.useStandardProviders();

    fixture.assertStructureEqual(fixture.getPackageDirectory(), """
      package1
       Class1
       Class2.java
        Class2
        Class3
       Class4.java
       Form1
       Form1.form
       Form2.form
      """);

    fixture.getStructure().setProviders();

    fixture.assertStructureEqual(fixture.getPackageDirectory(),
                         """
                           package1
                            Class1.java
                            Class2.java
                            Class4.java
                            Form1.form
                            Form1.java
                            Form2.form
                           """);

  }

  @Test
  public void testShowClassMembers() {
    ProjectViewFixture fixture = projectViewFixture.get();
    fixture.useStandardProviders();

    fixture.getStructure().setShowMembers(false);
    fixture.assertStructureEqual(fixture.getPackageDirectory(), """
      package1
       Class1
       Class2
      """);

    fixture.getStructure().setShowMembers(true);
    fixture.assertStructureEqual(fixture.getPackageDirectory(), """
      package1
       Class1
        InnerClass
         myInnerClassField
        getValue
        myField1
        myField2
       Class2
        InnerClass1
         InnerClass12
          InnerClass13
           InnerClass14
            InnerClass15
             myInnerClassField
            myInnerClassField
           myInnerClassField
          myInnerClassField
         myInnerClassField
        InnerClass2
         InnerClass22
          InnerClass23
           InnerClass24
            InnerClass25
             myInnerClassField
            myFieldToSelect
           myInnerClassField
          myInnerClassField
         myInnerClassField
        getValue
        myField1
        myField2
        myField3
        myField4
      """, 100);
  }

  @Test
  public void testGetParentObject() {
    ProjectViewFixture fixture = projectViewFixture.get();
    fixture.useStandardProviders();
    fixture.getStructure().setShowMembers(true);
    fixture.assertStructureEqual(fixture.getContentRootDirectory(), """
      getParentObject
       src
        com
         package1
          Class1
           method
           myField
          Class2.java
           Class2
           Class3
          Class4.java
           Class4
          Form1
          Form1.form
          Form2.form
      """);

    ReadAction.runBlocking(() -> checkContainsMethod(fixture.getStructure().getRootElement(), fixture.getStructure()));
  }

  @Test
  public void testNoDuplicateModules() {
    ProjectViewFixture fixture = projectViewFixture.get();
    VirtualFile mainModuleRoot = ModuleRootManager.getInstance(fixture.getModule()).getContentRoots()[0];

    PsiTestUtil.addExcludedRoot(fixture.getModule(), mainModuleRoot.findFileByRelativePath("src/com/package1/p2"));

    Module module = fixture.createModule("nested_module");

    ModifiableModuleModel moduleModel = ModuleManager.getInstance(fixture.getProject()).getModifiableModel();
    moduleModel.setModuleGroupPath(module, new String[]{"modules"});
    WriteAction.runAndWait(() -> moduleModel.commit());

    PsiTestUtil.addContentRoot(module, mainModuleRoot.findFileByRelativePath("src/com/package1/p2/p3"));

    fixture.getStructure().setShowLibraryContents(false);
    fixture.getStructure().hideExcludedFiles();

    fixture.assertStructureEqual("""
                           Project
                            noDuplicateModules
                             src
                              com
                               package1
                                Test.java
                           """);
  }

  @Test
  public void testContentRootUnderExcluded() {
    ProjectViewFixture fixture = projectViewFixture.get();
    VirtualFile mainModuleRoot = ModuleRootManager.getInstance(fixture.getModule()).getContentRoots()[0];

    PsiTestUtil.addExcludedRoot(fixture.getModule(), mainModuleRoot.findFileByRelativePath("exc"));

    PsiTestUtil.addContentRoot(fixture.getModule(), mainModuleRoot.findFileByRelativePath("exc/gen"));

    fixture.getStructure().setShowLibraryContents(false);

    fixture.assertStructureEqual("""
                           Project
                            contentRootUnderExcluded
                             B.txt
                             exc
                              excluded.txt
                              gen
                               A.java
                           """);

    fixture.getStructure().hideExcludedFiles();
    fixture.assertStructureEqual("""
                           Project
                            Module
                             contentRootUnderExcluded
                              B.txt
                             gen
                              A.java
                           """);
  }

  @Test
  public void testQualifiedModuleNames() {
    ProjectViewFixture fixture = projectViewFixture.get();
    VirtualFile testDataRoot = ModuleRootManager.getInstance(fixture.getModule()).getContentRoots()[0];
    Module a = fixture.createModule("a");
    PsiTestUtil.addContentRoot(a, testDataRoot.findFileByRelativePath("a"));

    Module main = fixture.createModule("a.main");
    PsiTestUtil.addContentRoot(main, testDataRoot.findFileByRelativePath("a/main"));

    Module foo = fixture.createModule("a.foo");
    PsiTestUtil.addContentRoot(foo, testDataRoot.findFileByRelativePath("a/Foo"));

    Module util = fixture.createModule("util");
    PsiTestUtil.addContentRoot(util, testDataRoot.findFileByRelativePath("a/util"));

    Module b = fixture.createModule("x.b");
    PsiTestUtil.addContentRoot(b, testDataRoot.findFileByRelativePath("a/b"));
    fixture.getStructure().setShowLibraryContents(false);

    //todo this function is generic enough, it can be moved to testFramework
    Function<Object, String> nodePresenter = o -> {
      AbstractTreeNode node = (AbstractTreeNode)o;
      node.update();
      PresentationData presentation = node.getPresentation();
      List<PresentableNodeDescriptor.ColoredFragment> fragments = presentation.getColoredText();
      if (fragments.isEmpty()) {
        return presentation.getPresentableText();
      }
      return StringUtil.join(fragments, PresentableNodeDescriptor.ColoredFragment::getText, "");
    };
    String treeStructure = ModuleGroupTestsKt.runWithQualifiedModuleNamesEnabled(
      () -> ReadAction.computeBlocking(() -> PlatformTestUtil.print(fixture.getStructure(), fixture.getStructure().getRootElement(), nodePresenter)));
    assertEquals("""
                   testQualifiedModuleNames
                    qualifiedModuleNames [testQualifiedModuleNames]
                     a
                      Foo
                       foo.txt
                      b [x.b]
                       b.txt
                      main
                       main.txt
                      util
                       util.txt
                   """,
                 treeStructure);
  }
}
