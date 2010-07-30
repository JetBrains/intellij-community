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
package com.intellij.lang.java.parser.partial;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.parser.ReferenceParser;
import com.intellij.pom.java.LanguageLevel;


public class ReferenceParserTest extends JavaParsingTestCase {
  public ReferenceParserTest() {
    super("parser-partial/references");
  }

  public void testReference0() { doRefParserTest("a", false); }
  public void testReference1() { doRefParserTest("a.", true); }
  public void testReference2() { doRefParserTest("a.b", false); }

  public void testType0() { doTypeParserTest("int", false); }
  public void testType1() { doTypeParserTest("a.b", false); }
  public void testType2() { doTypeParserTest("int[]", false); }
  public void testType3() { doTypeParserTest("int[][", false); }
  public void testType4() { doTypeParserTest("Map<String,List<String>>", false); }
  public void testType5() { doTypeParserTest("Object[]...", false); }
  public void testType6() {
    withLevel(LanguageLevel.JDK_1_7,
              new Runnable() { public void run() { doTypeParserTest("@English String @NonEmpty []", false); } });
  }

  public void testTypeParams0() { doTypeParamsParserTest("<T>"); }
  public void testTypeParams1() { doTypeParamsParserTest("<T, U>"); }
  public void testTypeParams2() { doTypeParamsParserTest("<T"); }
  public void testTypeParams3() { doTypeParamsParserTest("<T hack>"); }
  public void testTypeParams4() { doTypeParamsParserTest("<T hack"); }
  public void testTypeParams5() { doTypeParamsParserTest("<T extends X & Y<Z>>"); }
  public void testTypeParams6() { doTypeParamsParserTest("<T supers X>"); }
  public void testTypeParams7() { doTypeParamsParserTest("<T extends X, Y>"); }

  private void doRefParserTest(final String text, final boolean incomplete) {
    doParserTest(text, new TestParser() {
      public void parse(final PsiBuilder builder) {
        ReferenceParser.parseJavaCodeReference(builder, incomplete, false, false);
      }
    });
  }

  private void doTypeParserTest(final String text, final boolean incomplete) {
    doParserTest(text, new TestParser() {
      public void parse(final PsiBuilder builder) {
        ReferenceParser.parseTypeWithEllipsis(builder, incomplete, false);
      }
    });
  }

  private void doTypeParamsParserTest(final String text) {
    doParserTest(text, new TestParser() {
      public void parse(final PsiBuilder builder) {
        ReferenceParser.parseTypeParameters(builder);
      }
    });
  }
}
