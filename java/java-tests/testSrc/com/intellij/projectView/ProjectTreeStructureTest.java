// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    assertStructureEqual(getPackageDirectory(), "package1\n" +
                                                " Class2.java converted\n" +
                                                " Form1 joined\n" +
                                                "  Form1.form\n" +
                                                "  Form1.java converted\n" +
                                                " Form2.form\n");
  }

  public void testStandardProviders() {
    useStandardProviders();

    assertStructureEqual(getPackageDirectory(), "package1\n" +
                                                " Class1\n" +
                                                " Class2.java\n" +
                                                "  Class2\n" +
                                                "  Class3\n" +
                                                " Class4.java\n" +
                                                " Form1\n" +
                                                " Form1.form\n" +
                                                " Form2.form\n");

    getProjectTreeStructure().setProviders();

    assertStructureEqual(getPackageDirectory(),
                         "package1\n" +
                         " Class1.java\n" +
                         " Class2.java\n" +
                         " Class4.java\n" +
                         " Form1.form\n" +
                         " Form1.java\n" +
                         " Form2.form\n");

  }

  public void testShowClassMembers() {
    useStandardProviders();

    myStructure.setShowMembers(false);
    assertStructureEqual(getPackageDirectory(), "package1\n" +
                                                " Class1\n" +
                                                "  InnerClass\n" +
                                                " Class2\n" +
                                                "  InnerClass1\n" +
                                                "   InnerClass12\n" +
                                                "    InnerClass13\n" +
                                                "     InnerClass14\n" +
                                                "      InnerClass15\n" +
                                                "  InnerClass2\n" +
                                                "   InnerClass22\n" +
                                                "    InnerClass23\n" +
                                                "     InnerClass24\n" +
                                                "      InnerClass25\n");

    myStructure.setShowMembers(true);
    assertStructureEqual(getPackageDirectory(), "package1\n" +
                                                " Class1\n" +
                                                "  InnerClass\n" +
                                                "   myInnerClassField\n" +
                                                "  getValue\n" +
                                                "  myField1\n" +
                                                "  myField2\n" +
                                                " Class2\n" +
                                                "  InnerClass1\n" +
                                                "   InnerClass12\n" +
                                                "    InnerClass13\n" +
                                                "     InnerClass14\n" +
                                                "      InnerClass15\n" +
                                                "       myInnerClassField\n" +
                                                "      myInnerClassField\n" +
                                                "     myInnerClassField\n" +
                                                "    myInnerClassField\n" +
                                                "   myInnerClassField\n" +
                                                "  InnerClass2\n" +
                                                "   InnerClass22\n" +
                                                "    InnerClass23\n" +
                                                "     InnerClass24\n" +
                                                "      InnerClass25\n" +
                                                "       myInnerClassField\n" +
                                                "      myFieldToSelect\n" +
                                                "     myInnerClassField\n" +
                                                "    myInnerClassField\n" +
                                                "   myInnerClassField\n" +
                                                "  getValue\n" +
                                                "  myField1\n" +
                                                "  myField2\n" +
                                                "  myField3\n" +
                                                "  myField4\n", 100);
  }

  public void testGetParentObject() {
    useStandardProviders();
    myStructure.setShowMembers(true);
    assertStructureEqual(getContentDirectory(), "getParentObject\n" +
                                                " src\n" +
                                                "  com\n" +
                                                "   package1\n" +
                                                "    Class1\n" +
                                                "     method\n" +
                                                "     myField\n" +
                                                "    Class2.java\n" +
                                                "     Class2\n" +
                                                "     Class3\n" +
                                                "    Class4.java\n" +
                                                "    Form1\n" +
                                                "    Form1.form\n" +
                                                "    Form2.form\n");

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

    assertStructureEqual("Project\n" +
                         " noDuplicateModules\n" +
                         "  src\n" +
                         "   com\n" +
                         "    package1\n" +
                         "     Test.java\n");
  }

  public void testContentRootUnderExcluded() {
    VirtualFile mainModuleRoot = ModuleRootManager.getInstance(myModule).getContentRoots()[0];

    PsiTestUtil.addExcludedRoot(myModule, mainModuleRoot.findFileByRelativePath("exc"));

    PsiTestUtil.addContentRoot(myModule, mainModuleRoot.findFileByRelativePath("exc/gen"));

    myStructure.setShowLibraryContents(false);

    assertStructureEqual("Project\n" +
                         " contentRootUnderExcluded\n" +
                         "  B.txt\n" +
                         "  exc\n" +
                         "   excluded.txt\n" +
                         "   gen\n" +
                         "    A.java\n");

    myStructure.hideExcludedFiles();
    assertStructureEqual("Project\n" +
                         " Module\n" +
                         "  contentRootUnderExcluded\n" +
                         "   B.txt\n" +
                         "  gen\n" +
                         "   A.java\n");
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
    assertEquals("testQualifiedModuleNames\n" +
                 " qualifiedModuleNames [testQualifiedModuleNames]\n" +
                 "  a\n" +
                 "   Foo\n" +
                 "    foo.txt\n" +
                 "   b [x.b]\n" +
                 "    b.txt\n" +
                 "   main\n" +
                 "    main.txt\n" +
                 "   util\n" +
                 "    util.txt\n",
                 treeStructure);
  }
}
