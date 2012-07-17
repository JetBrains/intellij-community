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

import com.intellij.psi.codeStyle.rearranger.RearrangerEntry;
import com.intellij.psi.codeStyle.rearranger.RearrangerEntryType;
import com.intellij.psi.codeStyle.rearranger.RearrangerRule;
import com.intellij.psi.codeStyle.rearranger.TypeAwareRearrangerEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Filters {@link RearrangerEntry entries} by {@link TypeAwareRearrangerEntry#getType() their types}.
 * <p/>
 * <b>Note:</b> type-unaware entry will not be matched by the current rule.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/17/12 11:19 AM
 */
public class RearrangerTypeRule implements RearrangerRule {

  private final Set<RearrangerEntryType> myTypes = EnumSet.noneOf(RearrangerEntryType.class);

  public RearrangerTypeRule(@NotNull RearrangerEntryType ... interestedTypes) {
    myTypes.addAll(Arrays.asList(interestedTypes));
  }

  @Override
  public boolean isMatched(@NotNull RearrangerEntry entry) {
    if (entry instanceof TypeAwareRearrangerEntry) {
      return myTypes.contains(((TypeAwareRearrangerEntry)(entry)).getType());
    }
    return false;
  }
}
