// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.platform.templates.SaveProjectAsTemplateAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import gnu.trove.TIntObjectHashMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
public class FileTemplatesProcessingTest extends BasePlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PlatformTestUtil.setLongMeaninglessFileIncludeTemplateTemporarilyFor(getProject(), getTestRootDisposable());
  }

  public void testExtractTemplate() {
    doTest(true, "/** Special Velocity symbols # and $ should be escaped */\n" +
                 "\n" +
                 "/**\n" +
                 " * Created by Dmitry.Avdeev on 1/22/13.\n" +
                 " */\n" +
                 "public class Bar {\n" +
                 "}",
           "/** Special Velocity symbols \\# and #[[\\$]]# should be escaped */\n" +
           "\n" +
           "/**\n" +
           " * Created by ${USER} on ${DATE}.\n" +
           " */\n" +
           "\n" +
           "public class Bar {\n" +
           "}");
  }

  private void doTest(boolean shouldEscape, String input, String expected) {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).getDefaultTemplate(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    Pattern pattern = FileTemplateUtil.getTemplatePattern(template, getProject(), new TIntObjectHashMap<>());
    Matcher matcher = pattern.matcher(input);
    assertTrue(matcher.matches());
    String result = SaveProjectAsTemplateAction.convertTemplates(input, pattern, template.getText(), shouldEscape);
    assertEquals(expected, result);
  }

  public void testExtractTemplateUnescaped() {
    doTest(false, "/** Special Velocity symbols # and $ should not be escaped */\n" +
                  "\n" +
                  "/**\n" +
                  " * Created by Dmitry.Avdeev on 1/22/13.\n" +
                  " */\n" +
                  "public class Bar {\n" +
                  "}",
           "/** Special Velocity symbols # and $ should not be escaped */\n" +
           "\n" +
           "<IntelliJ_File_Header>\n" +
           "public class Bar {\n" +
           "}");
  }
}
