// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.platform.templates.SaveProjectAsTemplateAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

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
    doTest(true, """
             /** Special Velocity symbols # and $ should be escaped */

             /**
              * Created by Dmitry.Avdeev on 1/22/13.
              */
             public class Bar {
             }""",
           """
             /** Special Velocity symbols \\# and #[[\\$]]# should be escaped */

             /**
              * Created by ${USER} on ${DATE}.
              */

             public class Bar {
             }""");
  }

  private void doTest(boolean shouldEscape, String input, String expected) {
    FileTemplate template = FileTemplateManager.getInstance(getProject()).getDefaultTemplate(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    Pattern pattern = FileTemplateUtil.getTemplatePattern(template, getProject(), new Int2ObjectOpenHashMap<>());
    Matcher matcher = pattern.matcher(input);
    assertTrue(matcher.matches());
    String result = SaveProjectAsTemplateAction.convertTemplates(input, pattern, template.getText(), shouldEscape);
    assertEquals(expected, result);
  }

  public void testExtractTemplateUnescaped() {
    doTest(false, """
             /** Special Velocity symbols # and $ should not be escaped */

             /**
              * Created by Dmitry.Avdeev on 1/22/13.
              */
             public class Bar {
             }""",
           """
             /** Special Velocity symbols # and $ should not be escaped */

             <IntelliJ_File_Header>
             public class Bar {
             }""");
  }
}
