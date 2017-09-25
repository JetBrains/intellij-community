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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightCompletionTestCase;
import com.intellij.openapi.fileTypes.MockLanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class CustomFileTypeCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/customFileType/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testKeyWordCompletion() {
    configureByFile(BASE_PATH + "1.cs");
    checkResultByFile(BASE_PATH + "1_after.cs");

    configureByFile(BASE_PATH + "2.cs");
    checkResultByFile(BASE_PATH + "2_after.cs");
  }

  public void testWordCompletion() {
    configureByFile(BASE_PATH + "WordCompletion.cs");
    testByCount(2, "while", "whiwhiwhi");
  }

  public void testErlang() {
    configureByFile(BASE_PATH + "Erlang.erl");
    testByCount(2, "case", "catch");
  }

  public void testComment() {
    configureByFile(BASE_PATH + "foo.cs");
    testByCount(0, new String[] { null });
  }

  public void testEmptyFile() {
    configureFromFileText("a.cs", "<caret>");
    complete();
    testByCount(1, "abstract", "x");
  }

  public void testPlainTextSubstitution() {
    FileTypeManagerEx.getInstanceEx().registerFileType(MockLanguageFileType.INSTANCE, "xxx");
    try {
      configureFromFileText("a.xxx", "aaa a<caret>");
      complete();
      checkResultByText("aaa aaa<caret>");
    }
    finally {
      FileTypeManagerEx.getInstanceEx().unregisterFileType(MockLanguageFileType.INSTANCE);

    }
  }

}
