package com.intellij.projectView;

public class ProjectTreeStructureTest extends BaseProjectViewTestCase {

  public void test1() {
    getProjectTreeStructure().setProviders(new SameNamesJoiner(), new ClassNameConvertor(myProject));
    assertStructureEqual(getPackageDirectory(), "PsiDirectory: package1\n" +
                                  " Class2.java converted\n" +
                                  " Form1 joined\n" +
                                  "  Form1.java converted\n" +
                                  "  PsiFile(plain text):Form1.form\n" +
                                  " PsiFile(plain text):Form2.form\n");
  }

  public void _testStandardProviders() {
    useStandardProviders();

    assertStructureEqual(getPackageDirectory(), "PsiDirectory: package1\n" +
                                                " PsiClass:Class1\n" +
                                                " PsiClass:Form1\n" +
                                                " PsiFile(plain text):Form1.form\n" +
                                                " PsiFile(plain text):Form2.form\n" +
                                                " PsiJavaFile:Class2.java\n" +
                                                "  PsiClass:Class2\n" +
                                                "  PsiClass:Class3\n" +
                                                " PsiJavaFile:Class4.java\n");
    assertStructureEqual("Project\n" +
           " External Libraries\n" +
           "  Library: < java 1.4 >\n" +
           "   PsiDirectory: jsp-api.jar\n" +
           "    PsiDirectory: META-INF\n" +
           "     PsiFile(plain text):MANIFEST.MF\n" +
           "    PsiDirectory: javax\n" +
           "     PsiDirectory: servlet\n" +
           "      PsiDirectory: jsp\n" +
           "       PsiClass:ErrorData\n" +
           "       PsiClass:HttpJspPage\n" +
           "       PsiClass:JspContext\n" +
           "       PsiClass:JspEngineInfo\n" +
           "       PsiClass:JspException\n" +
           "       PsiClass:JspFactory\n" +
           "       PsiClass:JspPage\n" +
           "       PsiClass:JspTagException\n"
    );

    getProjectTreeStructure().setProviders();

    assertStructureEqual(getPackageDirectory(), "PsiDirectory: package1\n" +
           " PsiFile(plain text):Form1.form\n" +
           " PsiFile(plain text):Form2.form\n" +
           " PsiJavaFile:Class1.java\n" +
           " PsiJavaFile:Class2.java\n" +
           " PsiJavaFile:Class4.java\n" +
           " PsiJavaFile:Form1.java\n");

    assertStructureEqual("Project\n" +
           " External Libraries\n" +
           "  Library: < java 1.4 >\n" +
           "   PsiDirectory: jsp-api.jar\n" +
           "    PsiDirectory: META-INF\n" +
           "     PsiFile(plain text):MANIFEST.MF\n" +
           "    PsiDirectory: javax\n" +
           "     PsiDirectory: servlet\n" +
           "      PsiDirectory: jsp\n" +
           "       PsiDirectory: el\n" +
           "        PsiFile:ELException.class\n" +
           "        PsiFile:ELParseException.class\n" +
           "        PsiFile:Expression.class\n" +
           "        PsiFile:ExpressionEvaluator.class\n" +
           "        PsiFile:FunctionMapper.class\n" +
           "        PsiFile:VariableResolver.class\n" +
           "       PsiDirectory: resources\n");
  }

  public void testShowClassMembers() {
    useStandardProviders();

    myShowMembers = false;
    assertStructureEqual(getPackageDirectory(), "PsiDirectory: package1\n" +
                                                " PsiClass:Class1\n" +
                                                " PsiClass:Class2\n");

    myShowMembers = true;
    assertStructureEqual(getPackageDirectory(), "PsiDirectory: package1\n" +
                                                " PsiClass:Class1\n" +
                                                "  PsiClass:InnerClass\n" +
                                                "   PsiField:myInnerClassField\n" +
                                                "  PsiField:myField1\n" +
                                                "  PsiField:myField2\n" +
                                                "  PsiMethod:getValue\n" +
                                                " PsiClass:Class2\n" +
                                                "  PsiClass:InnerClass1\n" +
                                                "   PsiClass:InnerClass12\n" +
                                                "    PsiClass:InnerClass13\n" +
                                                "     PsiClass:InnerClass14\n" +
                                                "      PsiClass:InnerClass15\n" +
                                                "       PsiField:myInnerClassField\n" +
                                                "      PsiField:myInnerClassField\n" +
                                                "     PsiField:myInnerClassField\n" +
                                                "    PsiField:myInnerClassField\n" +
                                                "   PsiField:myInnerClassField\n" +
                                                "  PsiClass:InnerClass2\n" +
                                                "   PsiClass:InnerClass22\n" +
                                                "    PsiClass:InnerClass23\n" +
                                                "     PsiClass:InnerClass24\n" +
                                                "      PsiClass:InnerClass25\n" +
                                                "       PsiField:myInnerClassField\n" +
                                                "      PsiField:myFieldToSelect\n" +
                                                "     PsiField:myInnerClassField\n" +
                                                "    PsiField:myInnerClassField\n" +
                                                "   PsiField:myInnerClassField\n" +
                                                "  PsiField:myField1\n" +
                                                "  PsiField:myField2\n" +
                                                "  PsiField:myField3\n" +
                                                "  PsiField:myField4\n" +
                                                "  PsiMethod:getValue\n", 100);

    
  }


  public void testGetParentObject(){
    useStandardProviders();
    myShowMembers = true;
    assertStructureEqual(getContentDirectory(), "PsiDirectory: getParentObject\n" +
                                                " PsiDirectory: src\n" +
                                                "  PsiDirectory: com\n" +
                                                "   PsiDirectory: package1\n" +
                                                "    PsiClass:Class1\n" +
                                                "     PsiField:myField\n" +
                                                "     PsiMethod:method\n" +
                                                "    PsiClass:Form1\n" +
                                                "    PsiFile(plain text):Form1.form\n" +
                                                "    PsiFile(plain text):Form2.form\n" +
                                                "    PsiJavaFile:Class2.java\n" +
                                                "     PsiClass:Class2\n" +
                                                "     PsiClass:Class3\n" +
                                                "    PsiJavaFile:Class4.java\n");


    checkContainsMethod(myStructure.getRootElement(), myStructure);

  }

}
