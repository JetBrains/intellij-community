// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * @author ignatov
 */
public class FieldPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testSimple() {
    doTest();
  }

  public void testFoo() {
    doTest();
  }
  
  public void testAnnotated() {
    doTest();
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "field";
  }

  public static class ModFieldPostfixTemplateTest extends FieldPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }

    //mod command has different an end position for caret after renaming, because it can only rename PsiNamedElement
    @Override
    protected void checkAfterFile() {
      try {
        String expected = FileUtil.loadFile(new File(getTestDataPath(), getTestName(true) + "_after.java"))
          .replace("<caret>", "");
        myFixture.checkResult(expected, true);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
