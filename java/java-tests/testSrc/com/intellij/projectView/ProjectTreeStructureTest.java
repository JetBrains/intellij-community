package com.intellij.projectView;

import com.intellij.openapi.ui.Queryable;

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
}
