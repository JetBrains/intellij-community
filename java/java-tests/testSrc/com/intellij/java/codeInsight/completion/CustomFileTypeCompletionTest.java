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
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.openapi.fileTypes.MockLanguageFileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class CustomFileTypeCompletionTest extends LightFixtureCompletionTestCase {
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
    myFixture.assertPreferredCompletionItems(0, "while", "whiwhiwhi");
  }

  public void testErlang() {
    configureByFile(BASE_PATH + "Erlang.erl");
    myFixture.assertPreferredCompletionItems(0, "case", "catch");
  }

  public void testComment() {
    configureByFile(BASE_PATH + "foo.cs");
    assertEmpty(myFixture.getLookupElements());
  }

  public void testEmptyFile() {
    myFixture.configureByText("a.cs", "<caret>");
    complete();
    assertTrue(myFixture.getLookupElementStrings().contains("abstract"));
    assertFalse(myFixture.getLookupElementStrings().contains("x"));
  }

  public void testPlainTextSubstitution() {
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("a.xxx", MockLanguageFileType.INSTANCE, "aaa a<caret>", 0, true);
    myFixture.configureFromExistingVirtualFile(file.getViewProvider().getVirtualFile());
    complete();
    myFixture.checkResult("aaa aaa<caret>");
  }

}
