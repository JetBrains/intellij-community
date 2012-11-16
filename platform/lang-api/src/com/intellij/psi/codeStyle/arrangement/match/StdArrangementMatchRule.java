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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import org.jetbrains.annotations.NotNull;

/**
 * Arrangement rule which uses {@link StdArrangementEntryMatcher standard settings-based matcher}.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/28/12 2:59 PM
 */
public class StdArrangementMatchRule extends ArrangementMatchRule implements Cloneable {

  public StdArrangementMatchRule(@NotNull StdArrangementEntryMatcher matcher) {
    super(matcher);
  }

  public StdArrangementMatchRule(@NotNull StdArrangementEntryMatcher matcher, @NotNull ArrangementEntryOrderType type) {
    super(matcher, type);
  }

  @NotNull
  @Override
  public StdArrangementEntryMatcher getMatcher() {
    return (StdArrangementEntryMatcher)super.getMatcher();
  }

  @Override
  public StdArrangementMatchRule clone() {
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(getMatcher().getCondition().clone()), getOrderType());
  }
}
