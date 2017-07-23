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
}