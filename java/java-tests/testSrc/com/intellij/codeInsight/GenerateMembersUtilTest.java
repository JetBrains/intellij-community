package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GenerateMembersUtilTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/generateMembersUtil/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testBeforeInner() throws Exception {
    doTest();
  }

  public void testNoExtraEmptyLine() throws Exception {
    doTest();
  }

  public void testNoRemoveComment() throws Exception { doTest(); }

  public void testSCR5798() throws Exception { doTest(); }

  public void testSCR6491() throws Exception { doTest(); }
  public void testSCR6491_1() throws Exception { doTest(); }
  public void testSCR6491_2() throws Exception { doTest(); }

  public void testCaretAtDeclaration() throws Exception { doTest(); }
  public void testCaretAfterComment() throws Exception { doTest(); }

  private void doTest() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiMethod method = factory.createMethod("foo", PsiType.VOID);
    int offset = getEditor().getCaretModel().getOffset();
    List<GenerationInfo> list = Collections.<GenerationInfo>singletonList(new PsiGenerationInfo<PsiMethod>(method));
    List<GenerationInfo> members = GenerateMembersUtil.insertMembersAtOffset(getFile(), offset, list);
    members.get(0).positionCaret(myEditor, true);
    checkResultByFile(null, BASE_PATH + getTestName(false) + "_after.java", true);
  }

  public void testSetupGeneratedMethodNotOverridingInitialBody() throws Exception {
    String methodText = "public void tearDown() {\n //comment\n }";
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiMethod method = factory.createMethodFromText(methodText, null);
    GenerateMembersUtil.setupGeneratedMethod(method);
    assertEquals(methodText, method.getText());
    
    //empty template
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject())
      .createFileFromText(JavaLanguage.INSTANCE, "class A {void foo() {}}\n class B extends A {void foo() {}\n}");

    method = file.getClasses()[1].getMethods()[0];
    GenerateMembersUtil.setupGeneratedMethod(method);
    assertEquals("@Override void foo() {\n" +
                 "    super.foo();\n" +
                 "    }", method.getText());
  }
}
