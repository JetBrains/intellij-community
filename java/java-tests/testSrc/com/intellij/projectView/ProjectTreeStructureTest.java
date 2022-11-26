// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.module.ModuleGroupTestsKt;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.util.List;
import java.util.function.Function;

public class ProjectTreeStructureTest extends BaseProjectViewTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPrintInfo = new Queryable.PrintInfo();
  }

  public void test1() {
    getProjectTreeStructure().setProviders(new SameNamesJoiner(), new ClassNameConvertor(myProject));
    assertStructureEqual(getPackageDirectory(), """
      package1
       Class2.java converted
       Form1 joined
        Form1.form
        Form1.java converted
       Form2.form
      """);
  }

  public void testStandardProviders() {
    useStandardProviders();

    assertStructureEqual(getPackageDirectory(), """
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

    getProjectTreeStructure().setProviders();

    assertStructureEqual(getPackageDirectory(),
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

  public void testShowClassMembers() {
    useStandardProviders();

    myStructure.setShowMembers(false);
    assertStructureEqual(getPackageDirectory(), """
      package1
       Class1
       Class2
      """);

    myStructure.setShowMembers(true);
    assertStructureEqual(getPackageDirectory(), """
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

  public void testGetParentObject() {
    useStandardProviders();
    myStructure.setShowMembers(true);
    assertStructureEqual(getContentDirectory(), """
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
          Form1
          Form1.form
          Form2.form
      """);

    checkContainsMethod(myStructure.getRootElement(), myStructure);
  }

  public void testNoDuplicateModules() {
    VirtualFile mainModuleRoot = ModuleRootManager.getInstance(myModule).getContentRoots()[0];

    PsiTestUtil.addExcludedRoot(myModule, mainModuleRoot.findFileByRelativePath("src/com/package1/p2"));

    Module module = createModule("nested_module");

    ModifiableModuleModel moduleModel = ModuleManager.getInstance(myProject).getModifiableModel();
    moduleModel.setModuleGroupPath(module, new String[]{"modules"});
    WriteAction.runAndWait(() -> moduleModel.commit());

    PsiTestUtil.addContentRoot(module, mainModuleRoot.findFileByRelativePath("src/com/package1/p2/p3"));

    myStructure.setShowLibraryContents(false);
    myStructure.hideExcludedFiles();

    assertStructureEqual("""
                           Project
                            noDuplicateModules
                             src
                              com
                               package1
                                Test.java
                           """);
  }

  public void testContentRootUnderExcluded() {
    VirtualFile mainModuleRoot = ModuleRootManager.getInstance(myModule).getContentRoots()[0];

    PsiTestUtil.addExcludedRoot(myModule, mainModuleRoot.findFileByRelativePath("exc"));

    PsiTestUtil.addContentRoot(myModule, mainModuleRoot.findFileByRelativePath("exc/gen"));

    myStructure.setShowLibraryContents(false);

    assertStructureEqual("""
                           Project
                            contentRootUnderExcluded
                             B.txt
                             exc
                              excluded.txt
                              gen
                               A.java
                           """);

    myStructure.hideExcludedFiles();
    assertStructureEqual("""
                           Project
                            Module
                             contentRootUnderExcluded
                              B.txt
                             gen
                              A.java
                           """);
  }

  public void testQualifiedModuleNames() {
    VirtualFile testDataRoot = ModuleRootManager.getInstance(myModule).getContentRoots()[0];
    Module a = createModule("a");
    PsiTestUtil.addContentRoot(a, testDataRoot.findFileByRelativePath("a"));

    Module main = createModule("a.main");
    PsiTestUtil.addContentRoot(main, testDataRoot.findFileByRelativePath("a/main"));

    Module foo = createModule("a.foo");
    PsiTestUtil.addContentRoot(foo, testDataRoot.findFileByRelativePath("a/Foo"));

    Module util = createModule("util");
    PsiTestUtil.addContentRoot(util, testDataRoot.findFileByRelativePath("a/util"));

    Module b = createModule("x.b");
    PsiTestUtil.addContentRoot(b, testDataRoot.findFileByRelativePath("a/b"));
    myStructure.setShowLibraryContents(false);

    //todo[nik] this function is generic enough, it can be moved to testFramework
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
    String treeStructure = ModuleGroupTestsKt.runWithQualifiedModuleNamesEnabled(() -> PlatformTestUtil.print(myStructure, myStructure.getRootElement(), nodePresenter));
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
