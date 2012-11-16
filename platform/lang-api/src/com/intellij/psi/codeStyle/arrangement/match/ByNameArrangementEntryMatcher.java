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

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * @author Denis Zhdanov
 * @since 7/19/12 6:36 PM
 */
public class ByNameArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final String  myPattern;
  @NotNull private final Pattern myCompiledPattern;
  
  public ByNameArrangementEntryMatcher(@NotNull String pattern) {
    myPattern = pattern;
    myCompiledPattern = Pattern.compile(pattern);
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    if (entry instanceof NameAwareArrangementEntry) {
      String name = ((NameAwareArrangementEntry)entry).getName();
      if (name == null) {
        return false;
      }
      return myCompiledPattern.matcher(name).matches();
    }
    return false;
  }

  @NotNull
  public String getPattern() {
    return myPattern;
  }

  @Override
  public int hashCode() {
    return myPattern.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ByNameArrangementEntryMatcher that = (ByNameArrangementEntryMatcher)o;
    return myPattern.equals(that.myPattern);
  }

  @Override
  public String toString() {
    return String.format("with name like '%s'", myPattern);
  }
}
