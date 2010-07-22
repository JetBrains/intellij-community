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
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

import static com.intellij.JavaTestUtil.getJavaBuilder;


public abstract class JavaParsingTestCase extends ParsingTestCase {
  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public JavaParsingTestCase(@NonNls final String dataPath) {
    super(dataPath, "java");
    IdeaTestCase.initPlatformPrefix();
  }

  protected interface Parser {
    void parse(PsiBuilder builder);
  }

  protected void doParserTest(final String text, final Parser parser) {
    final String name = getTestName(false);

    final PsiBuilder builder = getJavaBuilder(text);
    final PsiBuilder.Marker root = builder.mark();

    parser.parse(builder);

    if (builder.getTokenType() != null) {
      final PsiBuilder.Marker unparsed = builder.mark();
      while (builder.getTokenType() != null) builder.advanceLexer();
      unparsed.error("Unparsed tokens");
    }

    root.done(JavaElementType.JAVA_FILE);

    final String raw = DebugUtil.treeToString(builder.getTreeBuilt(), false);
    final String tree = raw.replaceFirst("com.intellij.psi.util.PsiUtilBase\\S+", "PsiJavaFile:" + name + ".java");
    try {
      checkResult(name + ".txt", tree);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
