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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;

@SkipSlowTestLocally
public class CreateMethodFromUsagePerformanceTest extends LightQuickFixTestCase {

  public void testWithHugeNumberOfParameters() {
    PlatformTestUtil.startPerformanceTest("5000 args for a new method", 400_000, () -> {
      String text = "class Foo {{ f<caret>oo(" + StringUtil.repeat("\"a\", ", 5000) + " \"a\");}}";
      configureFromFileText("Foo.java", text);
      doAction("Create method 'foo' in 'Foo'");
    })
      .assertTiming();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  }

}
