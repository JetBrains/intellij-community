// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.codeInsight.daemon.indentGuide.IndentGuidesProvider.Guide;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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
import java.util.stream.Collectors;

public abstract class BaseIndentGuideTest extends LightJavaCodeInsightFixtureTestCase {

  protected void doTest(@NotNull String text,
                        @NotNull Function<JavaCodeInsightTestFixture, IndentGuidesProvider> guidesProviderFactory) {
    IndentGuideTestData testData = parse(text, myFixture.getProject());
    myFixture.configureByText(getTestName(false) + ".java", testData.myText);
    CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtilRt.EMPTY_INT_ARRAY, false);
    IndentGuidesProvider provider = guidesProviderFactory.apply(myFixture);
    List<Guide> guides = provider.getGuides();
    assertEquals("expected to find " + testData.myGuides.size() + " indent guides (" + testData.myGuides + ")" +
                 "but got " + guides.size() + " (" + guides + ")",
                 testData.myGuides.size(), guides.size());
    Map<Pair<Integer, Integer>, Integer> guidesByLines = guides.stream()
      .collect(Collectors.toMap(i -> new Pair<>(i.getStartLine(), i.getEndLine()), i -> i.getIndent()));

    for (Guide expectedGuide : testData.myGuides) {
      Integer startLine = expectedGuide.getStartLine();
      Integer endLine = expectedGuide.getEndLine();
      Integer actualIndent = guidesByLines.get(new Pair<>(startLine, endLine));
      assertNotNull("expected to find an indent guide at lines " + startLine + "-" + endLine, actualIndent);
      assertEquals("expected that indent guide descriptor at lines " +
                   startLine + "-" + endLine + " has indent " + expectedGuide.getIndent() +
                   " but got " + actualIndent,
                   expectedGuide.getIndent(), actualIndent);
    }
  }

  @NotNull
  @Contract("_, _ -> new")
  private static IndentGuideTestData parse(@NotNull String text, Project project) {
    StringBuilder sb = new StringBuilder();
    List<Guide> guides = new ArrayList<>();
    Map<Integer, Integer> prevLineIndents = new HashMap<>();
    String[] lines = text.split("\n");
    for (int nLine = 0; nLine < lines.length; nLine++) {
      String line = lines[nLine];
      int shift = 0;
      int textStart = 0;
      Map<Integer, Integer> endedGuides = new HashMap<>(prevLineIndents);
      int tabSize = CodeStyle.getSettings(project).getTabSize(JavaFileType.INSTANCE);
      int tabs = 0;
      for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        if (c == '\t') {
          // i was already incremented, so we need to decrement tab size
          tabs += (tabSize - 1);
          continue;
        }
        if (c != '|') continue;
        int indent = i + tabs - shift;
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

  private static final class IndentGuideTestData {

    private final String myText;
    private final List<Guide> myGuides;

    @Contract(pure = true)
    private IndentGuideTestData(String text, List<Guide> guides) {
      myText = text;
      myGuides = guides;
    }
  }
}
