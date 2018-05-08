/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.formatter.java;

import com.intellij.codeInsight.AbstractEnterActionTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

import java.io.IOException;

public class JavaEnterActionTest extends AbstractEnterActionTestCase {
  
  public void testEnterInsideAnnotationParameters() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;
    setCodeStyleSettings(settings);

    doTextTest("java", 
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class, <caret>)\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}", 
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class, \n" +
               "                <caret>)\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}");

    doTextTest("java",
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class, <caret>\n" +
               "  )\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}",
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class, \n" +
               "                <caret>\n" +
               "  )\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}");
  }

  public void testEnterInsideAnnotationParameters_AfterNameValuePairBeforeLparenth() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.ALIGN_MULTILINE_ANNOTATION_PARAMETERS = true;
    setCodeStyleSettings(settings);

    doTextTest("java",
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class<caret>)\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}",
               "public class T {\n" +
               "\n" +
               "  @Configurable(order = 25, \n" +
               "                validator = BigDecimalPercentValidator.class\n" +
               "  <caret>)\n" +
               "  public void run() {\n" +
               "  }\n" +
               "  \n" +
               "  \n" +
               "}");
  }

  public void testToCodeBlockLambda() throws Exception {
    doTextTest("java", "class Issue {\n" +
                       "public static void main(String[] args) {\n" +
                       "Arrays.asList().stream().collect(() -> {<caret> new ArrayList<>(), ArrayList::add, ArrayList::addAll);\n" +
                       "}\n" +
                       "}",
                        "class Issue {\n" +
                        "public static void main(String[] args) {\n" +
                        "Arrays.asList().stream().collect(() -> {\n" +
                        "    new ArrayList<>()\n" +
                        "}, ArrayList::add, ArrayList::addAll);\n" +
                        "}\n" +
                        "}");
  }

  public void testEnter_BetweenChainedMethodCalls() throws IOException {
    doTextTest("java",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                .theseChildrenArePullable(eventsListView)\n" +
               "                .listener(this)\n" +
               "                .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>\n" +
               "                .setup(mPullToRefreshLayout);\n" +
               "    }\n" +
               "}",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                .theseChildrenArePullable(eventsListView)\n" +
               "                .listener(this)\n" +
               "                .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())\n" +
               "                <caret>\n" +
               "                .setup(mPullToRefreshLayout);\n" +
               "    }\n" +
               "}");
  }
  
  public void testEnter_BetweenAlignedChainedMethodCalls() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_CHAINED_METHODS = true;
    setCodeStyleSettings(settings);

    doTextTest("java",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                              .theseChildrenArePullable(eventsListView)\n" +
               "                              .listener(this)\n" +
               "                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>\n" +
               "                              .setup(mPullToRefreshLayout);\n" +
               "    }\n" +
               "}",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                              .theseChildrenArePullable(eventsListView)\n" +
               "                              .listener(this)\n" +
               "                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())\n" +
               "                              <caret>\n" +
               "                              .setup(mPullToRefreshLayout);\n" +
               "    }\n" +
               "}");
  }
  
  public void testEnter_AfterLastChainedCall() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.ALIGN_MULTILINE_CHAINED_METHODS = true;
    setCodeStyleSettings(settings);

    doTextTest("java",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                              .theseChildrenArePullable(eventsListView)\n" +
               "                              .listener(this)\n" +
               "                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())<caret>\n" +
               "    }\n" +
               "}",
               "class T {\n" +
               "    public void main() {\n" +
               "        ActionBarPullToRefresh.from(getActivity())\n" +
               "                              .theseChildrenArePullable(eventsListView)\n" +
               "                              .listener(this)\n" +
               "                              .useViewDelegate(StickyListHeadersListView.class, new AbsListViewDelegate())\n" +
               "                              <caret>\n" +
               "    }\n" +
               "}");
  }

  public void testEnter_NewArgumentWithTabs() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.getIndentOptions().USE_TAB_CHARACTER = true;
    javaCommon.getIndentOptions().SMART_TABS = true;
    setCodeStyleSettings(settings);

    doTextTest("java",
               "class T {\n" +
               "\tvoid test(\n" +
               "\t\t\tint a,<caret>\n" +
               ") {}",
               "class T {\n" +
               "\tvoid test(\n" +
               "\t\t\tint a,\n" +
               "\t\t\t<caret>\n" +
               ") {}");
  }

  public void testEnter_AfterStatementWithoutBlock() throws IOException {
    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            while (true) <caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            while (true) \n" +
               "                <caret>\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            while (true) {<caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            while (true) {\n" +
               "                <caret>\n" +
               "            }\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            try {<caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        if (true)\n" +
               "            try {\n" +
               "                <caret>\n" +
               "            }\n" +
               "    }\n" +
               "}\n");
  }

  public void testEnter_AfterStatementWithLabel() throws IOException {
    // as prev
    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "lb:\n" +
               "        while (true) break lb;<caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "lb:\n" +
               "        while (true) break lb;\n" +
               "        <caret>\n" +
               "    }\n" +
               "}\n");

    // as block
    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "lb:  while (true) break lb;<caret>\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "lb:  while (true) break lb;\n" +
               "        <caret>\n" +
               "    }\n" +
               "}\n");
  }

  public void testEnter_inlineComment() throws IOException {
    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        /<caret>/\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        /\n" +
               "        <caret>/\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        <caret>//\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        \n" +
               "        <caret>//\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        //a<caret>b\n" +
               "    }\n" +
               "}\n",
               "class T {\n" +
               "    void test() {\n" +
               "        //a\n" +
               "        // <caret>b\n" +
               "    }\n" +
               "}\n");

    doTextTest("java",
               "class T {\n" +
               "    void test() {\n" +
               "        //<caret>",
               "class T {\n" +
               "    void test() {\n" +
               "        //\n" +
               "    <caret>");
    }  
  
  public void testEnter_NewArgumentWithTabsNoAlign() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaCommon = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaCommon.getIndentOptions().USE_TAB_CHARACTER = true;
    javaCommon.getIndentOptions().SMART_TABS = true;
    javaCommon.ALIGN_MULTILINE_PARAMETERS = false;
    setCodeStyleSettings(settings);

    doTextTest("java",
               "class T {\n" +
               "\tvoid test(\n" +
               "\t\t\tint a,<caret>\n" +
               ") {}",
               "class T {\n" +
               "\tvoid test(\n" +
               "\t\t\tint a,\n" +
               "\t\t\t<caret>\n" +
               ") {}");
  }

  public void testIdea179073() throws IOException {
    doTextTest("java",
               "ArrayList<String> strings = new ArrayList<>();\n" +
               "    strings.stream()\n" +
               "        .forEach((e) -> {<caret>\n" +
               "        });",

               "ArrayList<String> strings = new ArrayList<>();\n" +
               "    strings.stream()\n" +
               "        .forEach((e) -> {\n" +
               "            <caret>\n" +
               "        });");
  }

  public void testIdea187535() throws IOException {
    doTextTest(
      "java",

      "public class Main {\n" +
      "    void foo() {\n" +
      "        {\n" +
      "            int a = 1;\n" +
      "        }\n" +
      "        int b = 2;<caret>\n" +
      "    }\n" +
      "}"
      ,
      "public class Main {\n" +
      "    void foo() {\n" +
      "        {\n" +
      "            int a = 1;\n" +
      "        }\n" +
      "        int b = 2;\n" +
      "        <caret>\n" +
      "    }\n" +
      "}");
  }

  public void testIdea189059() throws IOException {
    doTextTest(
      "java",

      "public class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        String[] s =\n" +
      "                new String[] {<caret>};\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    public static void main(String[] args) {\n" +
      "        String[] s =\n" +
      "                new String[] {\n" +
      "                        <caret>\n" +
      "                };\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea108112() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    setCodeStyleSettings(settings);

    doTextTest(
      "java",

      "public class Test {\n" +
      "    public void bar() {\n" +
      "        boolean abc;\n" +
      "        while (abc &&<caret>) {\n" +
      "        }\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    public void bar() {\n" +
      "        boolean abc;\n" +
      "        while (abc &&\n" +
      "               <caret>) {\n" +
      "        }\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea153628() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    setCodeStyleSettings(settings);

    doTextTest(
      "java",

      "public class Test {\n" +
      "    public boolean hasInvalidResults() {\n" +
      "        return foo ||<caret>;\n" +
      "    }\n" +
      "}",

      "public class Test {\n" +
      "    public boolean hasInvalidResults() {\n" +
      "        return foo ||\n" +
      "               <caret>;\n" +
      "    }\n" +
      "}"
    );
  }

  public void testIdea115696() throws IOException {
    doTextTest(
      "java",

      "class T {\n" +
      "    private void someMethod() {\n" +
      "        System.out.println(\"foo\" +<caret>);\n" +
      "    }\n" +
      "\n" +
      "}",

      "class T {\n" +
      "    private void someMethod() {\n" +
      "        System.out.println(\"foo\" +\n" +
      "                <caret>);\n" +
      "    }\n" +
      "\n" +
      "}"
    );
  }

  public void testIdea115696_Aligned() throws IOException {
    CodeStyleSettings settings = getCodeStyleSettings();
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    setCodeStyleSettings(settings);

    doTextTest(
      "java",

      "class T {\n" +
      "    private void someMethod() {\n" +
      "        System.out.println(\"foo\" +<caret>);\n" +
      "    }\n" +
      "\n" +
      "}",

      "class T {\n" +
      "    private void someMethod() {\n" +
      "        System.out.println(\"foo\" +\n" +
      "                           <caret>);\n" +
      "    }\n" +
      "\n" +
      "}"
    );
  }
}