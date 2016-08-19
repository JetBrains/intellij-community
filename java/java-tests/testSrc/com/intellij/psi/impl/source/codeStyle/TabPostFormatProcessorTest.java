/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.LineSet;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Denis Zhdanov
 * @since 08/01/2012
 */
@RunWith(JMock.class)
public class TabPostFormatProcessorTest {

  private static final String START_RANGE_MARKER = "<range>";
  private static final String END_RANGE_MARKER   = "</range>";

  private Mockery                myMockery;
  private Document               myDocument;

  @Before
  public void setUp() {
    myMockery = new JUnit4Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myDocument = myMockery.mock(Document.class);
  }

  @After
  public void checkExpectations() {
    myMockery.assertIsSatisfied();
  }

  @Test
  public void spacesAndWholeLineInsideRange() {
    doTestSpaces(
      "line 1<range>\n" +
      " \t \tline2\n" +
      "line</range> 3",
      4,
      "line 1\n" +
      "          line2\n" +
      "line 3"
    );
  }
  @Test
  public void spacesAndExactRange() {
    doTestSpaces(
      "line 1\n" +
      "<range> \t \tline2</range>\n" +
      "line 3",
      4,
      "line 1\n" +
      "          line2\n" +
      "line 3"
    );
  }

  @Test
  public void spacesAndHeadIntersection() {
    doTestSpaces(
      "line 1<range>\n" +
      " \t </range>\tline2\n" +
      "line 3",
      4,
      "line 1\n" +
      "      \tline2\n" +
      "line 3"
    );
  }

  @Test
  public void spacesAndTailIntersection() {
    doTestSpaces(
      "line 1\n" +
      " \t <range>\tline2</range>\n" +
      "line 3",
      4,
      "line 1\n" +
      " \t     line2\n" +
      "line 3"
    );
  }

  @Test
  public void spacesAndPartialIndentInsideRange() {
    doTestSpaces(
      "line 1\n" +
      " \t <range>\t</range> \t line2\n" +
      "line 3",
      4,
      "line 1\n" +
      " \t      \t line2\n" +
      "line 3"
    );
  }
  
  @Test
  public void tabsAndWholeLineInsideRange() {
    doTestTabs(
      "line 1<range>\n" +
      "     \t   line2\n" +
      "line</range> 3",
      4,
      "line 1\n" +
      "\t \t   line2\n" +
      "line 3"
    );
  }
  
  @Test
  public void tabsAndHeadIntersection() {
    doTestTabs(
      "line 1<range>\n" +
      "    \t   </range>  line2\n" +
      "line 3",
      4,
      "line 1\n" +
      "\t\t     line2\n" +
      "line 3"
    );
  }
  
  @Test
  public void tabsAndTailIntersection() {
    doTestTabs(
      "line 1\n" +
      "   <range>     line2\n" +
      "</range>line 3",
      4,
      "line 1\n" +
      "   \t line2\n" +
      "line 3"
    );
  }
  
  @Test
  public void tabsAndPartialIndentInsideRange() {
    doTestTabs(
      "line 1\n" +
      "     <range>     </range>     line2\n" +
      "line 3",
      4,
      "line 1\n" +
      "     \t      line2\n" +
      "line 3"
    );
  }
  
  @Test
  public void smartTabsForTheFirstLine() {
    doTestSmartTabs(
      "         line 1\n" +
      "\t line 2",
      4,
      "\t\t line 1\n" +
      "\t line 2"
    );
  }
  
  @Test
  public void smartTabsFromUpperLine() {
    doTestSmartTabs(
      "\t\t line 1<range>\n" +
      "              2</range>",
      4,
      "\t\t line 1\n" +
      "\t\t      2"
    );
  }
  
  @Test
  public void smartTabsExactReplacement() {
    doTestSmartTabs(
      "\tline 1<range>\n" +
      "    line 2</range>",
      4,
      "\tline 1\n" +
      "\tline 2"
    );
  }
  
  @Test
  public void smartTabsMismatchedIndent() {
    doTestSmartTabs(
      " \tline 1<range>\n" +
      "\t     line 2</range>",
      4,
      " \tline 1\n" +
      "\t     line 2"
    );
  }
  
  @Test
  public void smartTabsPartialMatchedIndent() {
    doTestSmartTabs(
      "\t\tline 1\n" +
      "    <range>    line 2</range>",
      4,
      "\t\tline 1\n" +
      "    \tline 2"
    );
  }
  
  @Test
  public void smartTabsPartialMisMatchedIndent() {
    doTestSmartTabs(
      "\t\tline 1\n" +
      "     <range>    line 2</range>",
      4,
      "\t\tline 1\n" +
      "         line 2"
    );
  }
  
  private void doTestSpaces(@NotNull String initial, final int tabWidth, @NotNull String expected) {
    doTest(initial, expected, false, false, tabWidth);
  }

  private void doTestTabs(@NotNull String initial, final int tabWidth, @NotNull String expected) {
    doTest(initial, expected, true, false, tabWidth);
  }
  
  private void doTestSmartTabs(@NotNull String initial, final int tabWidth, @NotNull String expected) {
    doTest(initial, expected, true, true, tabWidth);
  }
  
  private void doTest(@NotNull String initial, @NotNull String expected, boolean useTabs, boolean smartTabs, int tabWidth) {
    doDocumentTest(initial, expected, useTabs, smartTabs, tabWidth);
    doPsiTest(initial, expected, useTabs, smartTabs, tabWidth);
  }
  
  private void doDocumentTest(@NotNull String initial, @NotNull String expected, boolean useTabs, boolean smartTabs, int tabWidth) {
    Pair<String, TextRange> pair = parse(initial);
    final StringBuilder text = new StringBuilder(pair.first);
    final TextRange range = pair.second;
    
    myMockery.checking(new Expectations() {{
      allowing(myDocument).getCharsSequence(); will(returnValue(text.toString()));
      allowing(myDocument).getTextLength(); will(returnValue(text.length()));
    }});
    
    final LineSet lines = LineSet.createLineSet(myDocument.getCharsSequence());
    myMockery.checking(new Expectations() {{
      allowing(myDocument).getLineNumber(with(any(int.class))); will(new CustomAction("getLineNumber()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return lines.findLineIndex((Integer)invocation.getParameter(0));
        }
      });
      allowing(myDocument).getLineStartOffset(with(any(int.class))); will(new CustomAction("getLineStartOffset()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return lines.getLineStart((Integer)invocation.getParameter(0));
        }
      });
      allowing(myDocument).getLineEndOffset(with(any(int.class))); will(new CustomAction("getLineEndOffset()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return lines.getLineEnd((Integer)invocation.getParameter(0));
        }
      });
      allowing(myDocument).replaceString(with(any(int.class)), with(any(int.class)), with(any(String.class)));
      will(new CustomAction("replaceString") {
        @Nullable
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          int start = (Integer)invocation.getParameter(0);
          int end = (Integer)invocation.getParameter(1);
          String newText = (String)invocation.getParameter(2);
          text.replace(start, end, newText);
          return null;
        }
      });
    }});
    
    TabPostFormatProcessor.processViaDocument(myDocument, range, useTabs, smartTabs, tabWidth);
    assertEquals(expected, text.toString());
  }

  private static Pair<String, TextRange> parse(@NotNull String text) {
    int rangeMarkerStart = text.indexOf(START_RANGE_MARKER);
    int rangeMarkerEnd = text.indexOf(END_RANGE_MARKER);
    final StringBuilder buffer = new StringBuilder();
    final TextRange range;
    if (rangeMarkerStart >= 0 && rangeMarkerEnd >= 0) {
      range = TextRange.create(rangeMarkerStart, rangeMarkerEnd - START_RANGE_MARKER.length());
      buffer.append(text.substring(0, rangeMarkerStart))
        .append(text.substring(rangeMarkerStart + START_RANGE_MARKER.length(), rangeMarkerEnd))
        .append(text.substring(rangeMarkerEnd + END_RANGE_MARKER.length()));
    }
    else {
      range = TextRange.create(0, text.length());
      buffer.append(text);
    }
    return Pair.create(buffer.toString(), range);
  }
  
  private void doPsiTest(@NotNull String initial, @NotNull String expected, boolean useTabs, boolean smartTabs, int tabWidth) {
    final List<ASTNode> children = new ArrayList<>();
    final List<StringBuilder> childrenText = new ArrayList<>();
    Pair<String, TextRange> pair = parse(initial);
    final String text = pair.first;
    int start = 0;
    boolean inWhiteSpace = initial.charAt(0) == ' ' || initial.charAt(0) == '\t';
    for (int i = 1; i <= text.length(); i++) {
      if (i == text.length() || ((StringUtil.isWhiteSpace(text.charAt(i))) ^ inWhiteSpace)) {
        final int childIndex = children.size();
        final int startOffset = start;
        childrenText.add(new StringBuilder(text.substring(start, i)));
        final ASTNode child = myMockery.mock(ASTNode.class, "child" + childIndex);
        children.add(child);
        final IElementType type = inWhiteSpace ? TokenType.WHITE_SPACE : TokenType.CODE_FRAGMENT;
        myMockery.checking(new Expectations() {{
          allowing(child).getElementType(); will(returnValue(type));
          allowing(child).getChars(); will(returnValue(childrenText.get(childIndex)));
          allowing(child).getTextLength(); will(returnValue(childrenText.get(childIndex).length()));
          allowing(child).getStartOffset(); will(returnValue(startOffset));
        }});
        inWhiteSpace = !inWhiteSpace;
        start = i;
      }
    }
    
    final ASTNode root = myMockery.mock(ASTNode.class);
    myMockery.checking(new Expectations() {{
      allowing(root).getFirstChildNode(); will(returnValue(children.get(0)));
      allowing(root).getTextLength(); will(returnValue(text.length()));
      allowing(root).getStartOffset(); will(returnValue(0));
    }});

    TabPostFormatProcessor.TreeHelper helper = new TabPostFormatProcessor.TreeHelper() {

      @Override
      public ASTNode prevLeaf(@NotNull ASTNode current) {
        int i = children.indexOf(current);
        return i > 0 ? children.get(i - 1) : null;
      }

      @Override
      public ASTNode nextLeaf(@NotNull ASTNode current) {
        int i = children.indexOf(current);
        return i < children.size() - 1 ? children.get(i + 1) : null;
      }

      @Override
      public ASTNode firstLeaf(@NotNull ASTNode startNode) {
        return root == startNode ? children.get(0) : null;
      }

      @Override
      public void replace(@NotNull String newText, @NotNull TextRange range, @NotNull ASTNode leaf) {
        int i = children.indexOf(leaf);
        childrenText.get(i).replace(range.getStartOffset() - leaf.getStartOffset(), range.getEndOffset() - leaf.getStartOffset(), newText);
      }
    };
    
    TabPostFormatProcessor.processViaPsi(root, pair.second, helper, useTabs, smartTabs, tabWidth);
    StringBuilder actual = new StringBuilder();
    for (ASTNode child : children) {
      actual.append(child.getChars());
    }
    assertEquals(expected, actual.toString());
  }
}
