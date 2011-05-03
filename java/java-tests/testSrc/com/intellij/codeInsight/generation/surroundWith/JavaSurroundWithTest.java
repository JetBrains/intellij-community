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

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

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
      doTest(type, String.format(template, StringUtil.capitalize(type.toFileName())));
    }
  }
  
  private void doTest(@NotNull SurroundType surroundType, @NotNull String fileName) throws Exception {
    configureByFile(BASE_PATH + fileName + ".java");
    SurroundWithHandler.invoke(getProject(), getEditor(), getFile(), surroundType.getSurrounder());
    checkResultByFile(BASE_PATH + fileName + "_after.java");
  }
}
