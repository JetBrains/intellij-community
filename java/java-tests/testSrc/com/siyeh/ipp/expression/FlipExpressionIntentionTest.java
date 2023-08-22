/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ipp.expression;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.expression.FlipExpressionIntention
 */
public class FlipExpressionIntentionTest extends IPPTestCase {
  public void testPrefix() { doTest(); }
  public void testPolyadic() { doTest(); }
  public void testNoChange() { assertIntentionNotAvailable(); }
  public void testNoException() { assertIntentionNotAvailable(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("flip.smth.intention.name1", "-");
  }

  @Override
  protected String getRelativePath() {
    return "expression/flip_expression";
  }
}
