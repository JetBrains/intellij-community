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
package com.intellij.codeInsight;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class ExternalJavadocUrls7Test extends ExternalJavadocUrlsTest {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  @Override
  public void testVarargs() {
    doTest("class Test {\n" +
           "  void <caret>foo(Class<?>... cl) { }\n" +
           "}",

           "foo(java.lang.Class...)", "foo(java.lang.Class<?>...)", "foo-java.lang.Class...-", "foo-java.lang.Class<?>...-"
    );
  }

  @Override
  public void testTypeParams() {
    doTest("class Test {\n" +
           "  <T> void <caret>sort(T[] a, Comparator<? super T> c) { }\n" +
           "}\n" +
           "class Comparator<X>{}",

           "sort(T[], Comparator)", "sort(T[], Comparator<? super T>)", "sort-T:A-Comparator-", "sort-T:A-Comparator<? super T>-"
    );
  }
}
