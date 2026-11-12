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
package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see PostfixPrefixIntention
 */
@TestApplication
public class PostfixPrefixIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "opassign/postfix_prefix", CommonQuickFixBundle.message("fix.replace.with.x", "++i"));

  @Test
  public void testSimple() { ipp.get().doTest(); }
  @Test
  public void testPrefixExpression() { ipp.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "i++")); }
  @Test
  public void testPrefixExpressionInSwitchExprJava12() { ipp.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "i++")); }
  @Test
  public void testIncomplete() { ipp.get().assertIntentionNotAvailable(); }
  @Test
  public void testUnaryExpression() { ipp.get().assertIntentionNotAvailable(); }
}
