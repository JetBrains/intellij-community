package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.java.codeInsight.daemon.indentGuide.IndentGuidesProvider.Guide;

public abstract class BaseIndentGuideTest extends LightJavaCodeInsightFixtureTestCase {

  protected void doTest(@NotNull String text,
                        @NotNull Function<JavaCodeInsightTestFixture, IndentGuidesProvider> guidesProviderFactory) {
    IndentGuideTestData testData = parse(text);
    myFixture.configureByText(getTestName(false) + ".java", testData.myText);
    CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtilRt.EMPTY_INT_ARRAY, false);
    IndentGuidesProvider provider = guidesProviderFactory.apply(myFixture);
    List<Guide> guides = provider.getGuides();
    assertEquals("expected to find " + testData.myGuides.size() + " indent guides (" + testData.myGuides + ")" +
                 "but got " + guides.size() + " (" + guides + ")",
                 testData.myGuides.size(), guides.size());

    for (Guide expectedGuide : testData.myGuides) {
      Integer startLine = expectedGuide.getStartLine();
      Integer endLine = expectedGuide.getEndLine();
      Integer actualIndent = provider.getIndentAt(startLine, endLine);
      assertNotNull("expected to find an indent guide at lines " + startLine + "-" + endLine, actualIndent);
      assertEquals("expected that indent guide descriptor at lines " +
                   startLine + "-" + endLine + " has indent " + expectedGuide.getIndent() +
                   " but got " + actualIndent,
                   expectedGuide.getIndent(), actualIndent);
    }
  }

  @NotNull
  @Contract("_ -> new")
  private static IndentGuideTestData parse(@NotNull String text) {
    StringBuilder sb = new StringBuilder();
    List<Guide> guides = new ArrayList<>();
    Map<Integer, Integer> prevLineIndents = new HashMap<>();
    String[] lines = text.split("\n");
    for (int nLine = 0; nLine < lines.length; nLine++) {
      String line = lines[nLine];
      int shift = 0;
      int textStart = 0;
      Map<Integer, Integer> endedGuides = new HashMap<>(prevLineIndents);
      for (int i = line.indexOf('|'); i >= 0; i = StringUtil.indexOf(line, '|', textStart)) {
        int indent = i - shift;
        if (prevLineIndents.containsKey(indent)) endedGuides.remove(indent);
        else prevLineIndents.put(indent, nLine - 1);
        shift++;
        sb.append(line, textStart, i);
        textStart = i + 1;
      }

      for (Map.Entry<Integer, Integer> endedGuide : endedGuides.entrySet()) {
        guides.add(new Guide(endedGuide.getValue(), nLine, endedGuide.getKey()));
        prevLineIndents.remove(endedGuide.getKey());
      }

      if (textStart < line.length()) sb.append(line.substring(textStart));
      sb.append("\n");
    }

    prevLineIndents.entrySet().stream().map(e -> new Guide(e.getValue(), lines.length, e.getKey())).forEach(guides::add);

    return new IndentGuideTestData(sb.toString(), guides);
  }

  private static class IndentGuideTestData {

    private final String myText;
    private final List<Guide> myGuides;

    @Contract(pure = true)
    private IndentGuideTestData(String text, List<Guide> guides) {
      myText = text;
      myGuides = guides;
    }
  }
}
