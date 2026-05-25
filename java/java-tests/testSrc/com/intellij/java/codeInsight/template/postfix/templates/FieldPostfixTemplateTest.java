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
