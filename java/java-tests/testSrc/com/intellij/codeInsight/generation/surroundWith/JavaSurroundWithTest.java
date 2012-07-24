/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 5/3/11 2:35 PM
 */
public class JavaSurroundWithTest extends LightCodeInsightTestCase {
  
  private static final String BASE_PATH = "/codeInsight/generation/surroundWith/java/";
  
  @SuppressWarnings({"UnusedDeclaration"})
  private enum SurroundType {
    IF(new JavaWithIfSurrounder()), IF_ELSE(new JavaWithIfElseSurrounder()),
    
    WHILE(new JavaWithWhileSurrounder()), DO_WHILE(new JavaWithDoWhileSurrounder()),
    
    FOR(new JavaWithForSurrounder()),
    
    TRY_CATCH(new JavaWithTryCatchSurrounder()), TRY_FINALLY(new JavaWithTryFinallySurrounder()), 
    TRY_CATCH_FINALLY(new JavaWithTryCatchFinallySurrounder()),

    SYNCHRONIZED(new JavaWithSynchronizedSurrounder()),

    RUNNABLE(new JavaWithRunnableSurrounder()),
    
    CODE_BLOCK(new JavaWithBlockSurrounder());

    private final Surrounder mySurrounder;

    SurroundType(Surrounder surrounder) {
      mySurrounder = surrounder;
    }

    public Surrounder getSurrounder() {
      return mySurrounder;
    }
    
    public String toFileName() {
      StringBuilder result = new StringBuilder();
      boolean capitalize = true;
      for (char c : toString().toCharArray()) {
        if (c == '_') {
          capitalize = true;
          continue;
        }
        if (capitalize) {
          result.append(Character.toUpperCase(c));
          capitalize = false;
        }
        else {
          result.append(Character.toLowerCase(c));
        }
      }
      return result.toString();
    }
  }
  
  public void testCommentAsFirstSurroundStatement() throws Exception {
    String template = "CommentAsFirst%sSurroundStatement";
    for (SurroundType type : SurroundType.values()) {
      doTest(String.format(template, StringUtil.capitalize(type.toFileName())), type.getSurrounder());
    }
  }

  public void testSurroundNonExpressionWithParenthesis() throws Exception {
    doTest(getTestName(false), new JavaWithParenthesesSurrounder());
  }

  public void testSurroundNonExpressionWithCast() throws Exception {
    doTest(getTestName(false), new JavaWithCastSurrounder());
  }

  public void testSurroundExpressionWithCastEmptyLineAfter() throws Exception {
    doTestWithTemplateFinish(getTestName(false), new JavaWithCastSurrounder(), "var");
  }

  public void testSurroundExpressionWithCastEmptyLineAfter_2() throws Exception {
    doTestWithTemplateFinish(getTestName(false), new JavaWithCastSurrounder(), null);
  }

  public void testSurroundNonExpressionWithNot() throws Exception {
    doTest(getTestName(false), new JavaWithNotSurrounder());
  }

  public void testSurroundBinaryWithCast() {
    final TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    templateManager.setTemplateTesting(true);
    try {
      doTest(getTestName(false), new JavaWithCastSurrounder());
    }
    finally {
      templateManager.setTemplateTesting(false);
    }
  }

  public void testSurroundConditionalWithCast() {
    final TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    templateManager.setTemplateTesting(true);
    try {
      doTest(getTestName(false), new JavaWithCastSurrounder());
    }
    finally {
      templateManager.setTemplateTesting(false);
    }
  }

  public void testSurroundAssignmentWithCast() {
    final TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    templateManager.setTemplateTesting(true);
    try {
      doTest(getTestName(false), new JavaWithCastSurrounder());
    }
    finally {
      templateManager.setTemplateTesting(false);
    }
  }

  public void testSurroundWithNotNullCheck() {
    final TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    templateManager.setTemplateTesting(true);
    try {
      doTest(getTestName(false), new JavaWithNullCheckSurrounder());
    }
    finally {
      templateManager.setTemplateTesting(false);
    }
  }

  private void doTest(@NotNull String fileName, final Surrounder surrounder) {
    configureByFile(BASE_PATH + fileName + ".java");
    SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surrounder);
    checkResultByFile(BASE_PATH + fileName + "_after.java");
  }

  private void doTestWithTemplateFinish(@NotNull String fileName, final Surrounder surrounder, @Nullable String textToType)
    throws Exception {
    final TemplateManagerImpl templateManager = (TemplateManagerImpl)TemplateManager.getInstance(getProject());
    try {
      templateManager.setTemplateTesting(true);
      configureByFile(BASE_PATH + fileName + ".java");
      SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surrounder);
      if (textToType != null) {
        type(textToType);
      }
      TemplateState templateState = TemplateManagerImpl.getTemplateState(getEditor());
      assertNotNull(templateState);
      templateState.nextTab();
      checkResultByFile(BASE_PATH + fileName + "_after.java");
    }
    finally {
      templateManager.setTemplateTesting(false);
    }
  }

}
