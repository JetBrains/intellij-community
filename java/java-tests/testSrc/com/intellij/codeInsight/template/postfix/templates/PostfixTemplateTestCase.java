/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

abstract public class PostfixTemplateTestCase extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/template/postfix/templates/" + getSuffix();
  }

  @Override
  final protected String getTestDataPath() {
    return super.getTestDataPath();
  }

  @NotNull
  protected abstract String getSuffix();

  protected void doTest() {
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.addClass("package java.lang;\n" +
                       "public final class Boolean implements java.io.Serializable, Comparable<Boolean> {}");
    myFixture.addClass("package java.lang;\n" +
                       "public final class Byte implements java.io.Serializable, Comparable<Byte> {}");
    myFixture.addClass("package java.lang;\n" +
                       "public interface Iterable<T> {}");
    myFixture.addClass("package java.util;\n" +
                       "public class ArrayList<E> extends AbstractList<E>\n" +
                       "        implements List<E>, Iterable<E>, RandomAccess, Cloneable, java.io.Serializable {}");
    myFixture.addClass("package java.lang;\n" +
                       "public interface AutoCloseable {}");
  }
}
