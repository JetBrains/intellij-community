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
package com.siyeh.ipp.collections;

import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceWithArraysAsListIntentionTest extends IPPTestCase {

  public void testBrokenCode() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return Collections.emptyList(\"text\"/*_Replace with 'java.util.Collections.singletonList()'*/);" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return Collections.singletonList(\"text\");" +
      "  }" +
      "}"
    );
  }

  public void testReplaceSingletonList() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return Collections.singletonList(\"text\"/*_Replace with 'java.util.Arrays.asList()'*/);" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return Arrays.asList(\"text\");" +
      "  }" +
      "}"
    );
  }

  public void testTypeParameters() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  private List<Object[]> rows;" +
      "    List<Object[]> getRows() {" +
      "    return rows == null ? /*_Replace with 'java.util.Arrays.asList()'*/Collections.<Object[]>emptyList() : rows;" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  private List<Object[]> rows;" +
      "    List<Object[]> getRows() {" +
      "    return rows == null ? Arrays.<Object[]>asList() : rows;" +
      "  }" +
      "}"
    );
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
