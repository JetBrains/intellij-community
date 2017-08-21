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
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class UnderscoresInLiteralsTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    super.tuneFixture(moduleBuilder);
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/underscoresInLiterals/";
  }

  public void testRemove() {
    final String removeIntention = CodeInsightBundle.message("intention.remove.literal.underscores");
    CodeInsightTestUtil.doIntentionTest(myFixture, removeIntention, "WithUnderscores.java", "WithoutUnderscores.java");
  }

  public void testInsert() {
    final String insertIntention = CodeInsightBundle.message("intention.insert.literal.underscores");
    CodeInsightTestUtil.doIntentionTest(myFixture, insertIntention, "WithoutUnderscores.java", "WithUnderscores.java");
  }
}
