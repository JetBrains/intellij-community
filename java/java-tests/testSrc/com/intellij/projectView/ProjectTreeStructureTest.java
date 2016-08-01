/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.projectView;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.junit.Assert;

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
                                                " Class2\n");

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
    ModuleManagerImpl.getInstanceImpl(myProject).setModuleGroupPath(module, new String[]{"modules"});
    PsiTestUtil.addContentRoot(module, mainModuleRoot.findFileByRelativePath("src/com/package1/p2/p3"));

    TestProjectTreeStructure structure = new TestProjectTreeStructure(myProject, getTestRootDisposable());
    structure.setShowLibraryContents(false);

    String structureContent = PlatformTestUtil.print(structure, structure.getRootElement(), 0, null, 10, ' ', myPrintInfo).toString();

    Assert.assertFalse(structureContent.contains("modules"));
    assertEquals("Project\n" +
                 " noDuplicateModules\n" +
                 "  src\n" +
                 "   com\n" +
                 "    package1\n" +
                 "     Test.java\n" +
                 " nested_module.iml\n" +
                 " testNoDuplicateModules.iml\n",
                 structureContent);
  }
}
