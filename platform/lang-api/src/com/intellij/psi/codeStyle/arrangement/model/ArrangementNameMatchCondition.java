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
package com.intellij.psi.codeStyle.arrangement.model;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Condition which works on {@link NameAwareArrangementEntry}
 * 
 * @author Denis Zhdanov
 * @since 11/14/12 12:18 PM
 */
public class ArrangementNameMatchCondition implements ArrangementMatchCondition {
  
  @NotNull private final String myPattern;

  public ArrangementNameMatchCondition(@NotNull String pattern) {
    myPattern = pattern;
  }

  @NotNull
  public String getPattern() {
    return myPattern;
  }

  @Override
  public void invite(@NotNull ArrangementMatchConditionVisitor visitor) {
    visitor.visit(this); 
  }

  @NotNull
  @Override
  public ArrangementMatchCondition clone() {
    return new ArrangementNameMatchCondition(myPattern);
  }

  @Override
  public int hashCode() {
    return myPattern.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrangementNameMatchCondition condition = (ArrangementNameMatchCondition)o;
    return myPattern.equals(condition.myPattern);
  }

  @Override
  public String toString() {
    return String.format("name like '%s'", StringUtil.escapeStringCharacters(myPattern));
  }
}
