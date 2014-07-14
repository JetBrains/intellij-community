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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.testFramework.FileBasedTestCaseHelperEx;
import com.intellij.testFramework.Parameterized;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Parameterized.class)
public abstract class LightQuickFixParameterizedTestCase extends LightQuickFixTestCase implements FileBasedTestCaseHelperEx {
  @Override
  public String getRelativeBasePath() {
    return getBasePath();
  }

  @Nullable
  @Override
  public String getFileSuffix(String fileName) {
    if (!fileName.startsWith(BEFORE_PREFIX)) return null;
    return fileName.substring(BEFORE_PREFIX.length());
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void doAllTests() {
    super.doAllTests();
  }

  @SuppressWarnings("JUnit4AnnotatedMethodInJUnit3TestCase")
  @Test
  public void runSingle() throws Throwable {
    runSingleTest(new Runnable() {
      @Override
      public void run() {
        doSingleTest(myFileSuffix, myTestDataPath);
      }
    });
  }
}
