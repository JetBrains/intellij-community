package com.intellij.execution.filters;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.Consumer;

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
    final ArrayList<String> result = new ArrayList<String>();
    filter.applyHeavyFilter(document, 0, 0, new Consumer<FilterMixin.AdditionalHighlight>() {
      @Override
      public void consume(FilterMixin.AdditionalHighlight highlight) {
        result.add(new TextRange(highlight.getStart(), highlight.getEnd()-1).substring(testData));
      }
    });
    assertSameElements(result, "com.sample.RunningMain.func1", "com.sample.RunningMain.main");
  }
}
