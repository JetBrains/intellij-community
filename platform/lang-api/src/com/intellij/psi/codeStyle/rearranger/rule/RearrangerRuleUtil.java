/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.rearranger.rule;

import com.intellij.psi.codeStyle.rearranger.RearrangerRule;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 7/17/12 11:24 AM
 */
public class RearrangerRuleUtil {

  private RearrangerRuleUtil() {
  }
  
  @NotNull
  public static RearrangerRule or(@NotNull RearrangerRule ... rules) {
    return combine(RearrangerCompositeRule.Operator.OR, rules);
  }

  @NotNull
  public static RearrangerRule and(@NotNull RearrangerRule ... rules) {
    return combine(RearrangerCompositeRule.Operator.AND, rules);
  }

  @NotNull
  private static RearrangerRule combine(@NotNull RearrangerCompositeRule.Operator operator, @NotNull RearrangerRule... rules) {
    RearrangerCompositeRule composite = null;
    for (RearrangerRule rule : rules) {
      if (rule instanceof RearrangerCompositeRule && ((RearrangerCompositeRule)(rule)).getOperator() == operator) {
        composite = (RearrangerCompositeRule)rule;
        break;
      }
    }

    if (composite == null) {
      return new RearrangerCompositeRule(operator, rules);
    }

    for (RearrangerRule rule : rules) {
      if (rule != composite) {
        composite.addRule(rule);
      }
    }
    return composite;
  }
}
