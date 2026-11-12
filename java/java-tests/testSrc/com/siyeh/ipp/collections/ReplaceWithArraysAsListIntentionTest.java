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

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
public class ReplaceWithArraysAsListIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  public void testBrokenCode() {
    ipp.get().doTest(
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

  @Test
  public void testReplaceSingletonList() {
    ipp.get().doTest(
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

  @Test
  public void testTypeParameters() {
    ipp.get().doTest(
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
}
