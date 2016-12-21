package com.intellij.execution.filters;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.ArrayList;

/**
 * @author gregsh
 */
public class ExceptionWorkerTest extends LightCodeInsightFixtureTestCase {

  public void testParsing() {
    myFixture.addClass("package com.sample;\n" +
                       "\n" +
                       "/**\n" +
                       " * Created with IntelliJ IDEA.\n" +
                       " * User: jetbrains\n" +
                       " * Date: 11/26/12\n" +
                       " * Time: 6:08 PM\n" +
                       " * To change this template use File | Settings | File Templates.\n" +
                       " */\n" +
                       "public class RunningMain {\n" +
                       "  public static void main(String[] args) throws Exception {\n" +
                       "    try {\n" +
                       "      func1();\n" +
                       "    }\n" +
                       "    finally {\n" +
                       "\n" +
                       "    }\n" +
                       "  }\n" +
                       "\n" +
                       "  static void func1() {\n" +
                       "    try {\n" +
                       "      func();\n" +
                       "    }\n" +
                       "    finally {\n" +
                       "\n" +
                       "    }\n" +
                       "  }\n" +
                       "\n" +
                       "  static void func() {\n" +
                       "    throw new NullPointerException();\n" +
                       "  }\n" +
                       "}\n");

    final String testData = "Exception in thread \"main\" java.lang.NullPointerException\n" +
                            "\tat com.sample.RunningMain.func(RunningMain.java:30)\n" +
                            "\tat com.sample.RunningMain.func1(RunningMain.java:22)\n" +
                            "\tat com.sample.RunningMain.main(RunningMain.java:13)\n";
    final Document document = EditorFactory.getInstance().createDocument(testData);
    FilterMixin filter = (FilterMixin)new ExceptionExFilterFactory().create(GlobalSearchScope.projectScope(getProject()));
    final ArrayList<String> result = new ArrayList<>();
    filter.applyHeavyFilter(document, 0, 0, r -> r.getResultItems().forEach(
      highlight -> result.add(new TextRange(highlight.getHighlightStartOffset(), highlight.getHighlightEndOffset()).substring(testData))));
    assertSameElements(result, "com.sample.RunningMain.func1", "com.sample.RunningMain.main");
  }

  public void testAnomalyParenthesisParsing() {
    String[][] data = new String[][]{
      {"at youtrack.jetbrains.com.Issue.IDEA_125137()(FooTest.groovy:2)", "youtrack.jetbrains.com.Issue", "IDEA_125137()",
        "FooTest.groovy:2"},
      {"at youtrack.jetbrains.com.Issue.IDEA_125137()Hmm(FooTest.groovy:2)", "youtrack.jetbrains.com.Issue", "IDEA_125137()Hmm",
        "FooTest.groovy:2"},
      {"p1.Cl.mee(p1.Cl.java:87) (A MESSAGE) IDEA-133794 (BUG START WITH 1)", "p1.Cl", "mee", "p1.Cl.java:87"}
    };
    for (String[] datum : data) {
      Trinity<TextRange, TextRange, TextRange> trinity = ExceptionWorker.parseExceptionLine(datum[0]);
      assertNotNull(trinity);
      assertEquals(datum[1], trinity.first.subSequence(datum[0]));
      assertEquals(datum[2], trinity.second.subSequence(datum[0]));
      assertEquals(datum[3], trinity.third.subSequence(datum[0]));
    }
  }
}
