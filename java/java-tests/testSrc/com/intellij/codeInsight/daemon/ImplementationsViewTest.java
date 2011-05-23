package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.idea.Bombed;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.Calendar;

/**
 * User: anna
 */
public class ImplementationsViewTest extends LightCodeInsightFixtureTestCase {
  @Bombed(day = 25, month = Calendar.MAY, user = "peter")
  public void testFromCompletion() {
    myFixture.configureByText("a.java", "public class Foo {\n" +
                                        "    private final String text;\n" +
                                        "\n" +
                                        "    public Foo(String text) {\n" +
                                        "//        this.text = text;\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    public Foo(int i) {\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    public static void main(String[] args) {\n" +
                                        "        final Foo foo = new Foo(\"\");\n" +
                                        "        foo.to<caret>\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    @Override\n" +
                                        "    public String toString() {\n" +
                                        "        return \"text\";\n" +
                                        "    }\n" +
                                        "    public void totttt(){}" +
                                        "}");
    myFixture.completeBasic();

    PsiElement element =
      TargetElementUtilBase.findTargetElement(myFixture.getEditor(), TargetElementUtilBase.getInstance().getAllAccepted());

    assert element != null;
    final String newText = ImplementationViewComponent.getNewText(element);

    assertEquals("    public String toString() {\n" +
                 "        return \"text\";\n" +
                 "    }\n", newText);
  }

  public void testFromEditor() {
    myFixture.configureByText("a.java", "public class Foo {\n" +
                                        "    private final String text;\n" +
                                        "\n" +
                                        "    public Foo(String text) {\n" +
                                        "//        this.text = text;\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    public Foo(int i) {\n" +
                                        "    }\n" +
                                        "\n" +
                                        "    @Override\n" +
                                        "    public String to<caret>String() {\n" +
                                        "        return \"text\";\n" +
                                        "    }\n" +
                                        "}");
    PsiElement element =
      TargetElementUtilBase.findTargetElement(myFixture.getEditor(), TargetElementUtilBase.getInstance().getAllAccepted());

    assert element != null;
    final String newText = ImplementationViewComponent.getNewText(element);

    assertEquals("    @Override\n" +
                 "    public String toString() {\n" +
                 "        return \"text\";\n" +
                 "    }", newText);
  }
}
