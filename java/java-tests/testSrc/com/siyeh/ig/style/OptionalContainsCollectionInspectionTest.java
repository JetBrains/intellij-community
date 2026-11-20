/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.style;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class OptionalContainsCollectionInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new OptionalContainsCollectionInspection(), getEnvironmentClasses());

  @Test
  public void testArray() {
    inspectionFixture.get().doTest("import java.util.Optional;" +
           "class X {" +
           "  Optional</*'Optional' contains array 'String[]'*/String[]/**/> a() {" +
           "    return Optional.empty();" +
           "  }" +
           "}");
  }

  @Test
  public void testMap() {
    inspectionFixture.get().doTest("import java.util.Optional;" +
           "import java.util.Map;" +
           "class X {" +
           "  Optional</*'Optional' contains collection 'Map<String, String>'*/Map<String, String>/**/> a() {" +
           "    return Optional.empty();" +
           "  }" +
           "}");
  }

  @Test
  public void testNoWarning() {
    inspectionFixture.get().doTest("import java.util.Optional;" +
           "class X {" +
           "  Optional<String> a() {" +
           "    return Optional.empty();" +
           "  }" +
           "}");
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "package java.util;" +
      "public final class Optional<T> {" +
      "    public static<T> Optional<T> empty() {" +
      "        return new Optional<>();" +
      "    }" +
      "}"
    };
  }
}