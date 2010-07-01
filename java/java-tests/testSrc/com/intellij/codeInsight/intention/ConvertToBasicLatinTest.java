/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;


public class ConvertToBasicLatinTest extends JavaCodeInsightFixtureTestCase {

  private String myIntention;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIntention = CodeInsightBundle.message("intention.convert.to.basic.latin");
    EncodingProjectManager.getInstance().setDefaultCharsetName("UTF-8");
  }

  public void testConvertCharLiteral() throws Exception {
    CodeInsightTestUtil.doIntentionTest(myFixture, myIntention, "CharLiteral.java", "CharLiteral_after.java");
  }

  public void testConvertStringLiteral() throws Exception {
    CodeInsightTestUtil.doIntentionTest(myFixture, myIntention, "StringLiteral.java", "StringLiteral_after.java");
  }

  public void testConvertPlainComment() throws Exception {
    CodeInsightTestUtil.doIntentionTest(myFixture, myIntention, "PlainComment.java", "PlainComment_after.java");
  }

  public void testConvertDocComment() throws Exception {
    CodeInsightTestUtil.doIntentionTest(myFixture, myIntention, "DocComment.java", "DocComment_after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/convertToBasicLatin/";
  }

}
