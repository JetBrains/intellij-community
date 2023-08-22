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
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceConcatenationWithFormatStringIntentionTest extends IPPTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_13;
  }

  public void testNarrowingCast() {
    doTest("class X {" +
           "  String s = (byte)321 +/*_Replace '+' with 'String.format()'*/ \" parsecs\";" +
           "}",

           "class X {" +
           "  String s = String.format(\"%s parsecs\", (byte) 321);" +
           "}"
           );
  }

  public void testNarrowingCastTextBlock() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, () -> {
      doTest("class X {" +
             "  String s = (byte)321 +/*_Replace '+' with 'formatted()'*/ \" parsecs\";" +
             "}",

             "class X {" +
             "  String s = \"%s parsecs\".formatted((byte) 321);" +
             "}"
      );
    });
  }

  public void testWideningCast() {
    doTest("class X {" +
           "  String s = (long)42 /*_Replace '+' with 'String.format()'*/+ \" the answer to life, the universe and everything\";" +
           "}",

           "class X {" +
           "  String s = String.format(\"%d the answer to life, the universe and everything\", 42);" +
           "}");
  }

  public void testCastToChar() {
    doTest("class X {" +
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