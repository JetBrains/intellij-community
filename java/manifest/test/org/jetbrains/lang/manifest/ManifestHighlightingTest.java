/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.lang.manifest;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class ManifestHighlightingTest extends LightCodeInsightFixtureTestCase {
  public void testHeaders() {
    doTest(
      "Normal-Header: value\n" +
      "Empty_Header: \n" +
      "<error descr=\"Invalid header name\">Extra-Space </error>: value\n" +
      "<error descr=\"Invalid header name\">Other*Header</error>: value\n");
  }

  public void testMainClass() {
    doTest(
      "Main-Class: <error descr=\"Invalid reference\"></error>\n" +
      "Main-Class: <error descr=\"Cannot resolve class 'org.acme.Main'\">org.acme.Main</error>\n" +
      "Main-Class: <error descr=\"Invalid main class\">java.lang.String</error>\n" +
      "Main-Class: pkg.C1\n");
  }

  public void testAgentHeaders() {
    doTest(
      "Premain-Class: <error descr=\"Invalid pre-main class\">pkg.C1</error>\n" +
      "Premain-Class: pkg.C2\n" +
      "Agent-Class: <error descr=\"Invalid agent class\">pkg.C1</error>\n" +
      "Agent-Class: pkg.C3\n");
  }

  private void doTest(String text) {
    myFixture.addClass("package pkg;\n\nclass C1 { public static void main(String... args) { } }");
    myFixture.addClass("package pkg;\n\nclass C2 { public static void premain() { } }");
    myFixture.addClass("package pkg;\n\nclass C3 { public static void agentmain() { } }");
    myFixture.configureByText("MANIFEST.MF", text);
    myFixture.checkHighlighting(true, false, false);
  }
}