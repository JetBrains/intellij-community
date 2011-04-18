/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.openapi.util.ClassConditionKey;

/**
 * @author peter
 */
public class MatchedLookupElement extends LookupElementDecorator<LookupElement> {
  public static final ClassConditionKey<MatchedLookupElement> CLASS_CONDITION_KEY = ClassConditionKey.create(MatchedLookupElement.class);
  private final PrefixMatcher myMatcher;
  private final CompletionSorter mySorter;

  MatchedLookupElement(LookupElement delegate, PrefixMatcher matcher, CompletionSorter sorter) {
    super(delegate);
    myMatcher = matcher;
    mySorter = sorter;
  }

  public PrefixMatcher getPrefixMatcher() {
    return myMatcher;
  }

  public CompletionSorter getSorter() {
    return mySorter;
  }

}
