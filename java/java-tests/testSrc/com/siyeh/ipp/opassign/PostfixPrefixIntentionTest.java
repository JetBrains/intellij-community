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
import com.siyeh.ipp.IPPTestCase;

/**
 * @see PostfixPrefixIntention
 */
public class PostfixPrefixIntentionTest extends IPPTestCase {
  public void testSimple() { doTest(); }
  public void testPrefixExpression() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "i++")); }
  public void testPrefixExpressionInSwitchExprJava12() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "i++")); }
  public void testIncomplete() { assertIntentionNotAvailable(); }
  public void testUnaryExpression() { assertIntentionNotAvailable(); }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", "++i");
  }

  @Override
  protected String getRelativePath() {
    return "opassign/postfix_prefix";
  }
}
