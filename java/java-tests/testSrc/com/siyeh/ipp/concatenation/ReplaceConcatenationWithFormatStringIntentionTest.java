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
package com.siyeh.ipp.concatenation;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReplaceConcatenationWithFormatStringIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_13);
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testNarrowingCast() {
    ipp.get().doTest("class X {" +
           "  String s = (byte)321 +/*_Replace '+' with 'String.format()'*/ \" parsecs\";" +
           "}",

           "class X {" +
           "  String s = String.format(\"%s parsecs\", (byte) 321);" +
           "}"
           );
  }

  @Test
  public void testNarrowingCastTextBlock() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_15, () -> {
      ipp.get().doTest("class X {" +
             "  String s = (byte)321 +/*_Replace '+' with 'formatted()'*/ \" parsecs\";" +
             "}",

             "class X {" +
             "  String s = \"%s parsecs\".formatted((byte) 321);" +
             "}"
      );
    });
  }

  @Test
  public void testWideningCast() {
    ipp.get().doTest("class X {" +
           "  String s = (long)42 /*_Replace '+' with 'String.format()'*/+ \" the answer to life, the universe and everything\";" +
           "}",

           "class X {" +
           "  String s = String.format(\"%d the answer to life, the universe and everything\", 42);" +
           "}");
  }

  @Test
  public void testCastToChar() {
    ipp.get().doTest("class X {" +
           "  static String deepThought(byte b) {" +
           "    return (char)b/*_Replace '+' with 'String.format()'*/ + \" the answer to life, the universe and everything\";" +
           "  }" +
           "}",

           "class X {" +
           "  static String deepThought(byte b) {" +
           "    return String.format(\"%s the answer to life, the universe and everything\", (char) b);" +
           "  }" +
           "}");
  }

}