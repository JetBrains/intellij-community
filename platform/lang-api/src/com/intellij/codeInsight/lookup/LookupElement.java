/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * A typical way to create lookup element is to use {@link com.intellij.codeInsight.lookup.LookupElementBuilder}. 
 * Another way is to subclass it. Use the latter way only if you need it to implement some additional interface, to modify equals/hashCode
 * or other advanced logic
 *
 * @author peter
 */
public abstract class LookupElement extends UserDataHolderBase {
  public static final LookupElement[] EMPTY_ARRAY = new LookupElement[0];
  private PrefixMatcher myPrefixMatcher = PrefixMatcher.FALSE_MATCHER;

  @NotNull
  public abstract String getLookupString();

  public Set<String> getAllLookupStrings() {
    return Collections.singleton(getLookupString());
  }

  public boolean setPrefixMatcher(@NotNull final PrefixMatcher matcher) {
    myPrefixMatcher = matcher;
    return isPrefixMatched();
  }

  public final boolean isPrefixMatched() {
    return myPrefixMatcher.prefixMatches(this);
  }

  @NotNull
  public final PrefixMatcher getPrefixMatcher() {
    return myPrefixMatcher;
  }

  @NotNull
  public Object getObject() {
    return this;
  }

  public void handleInsert(InsertionContext context) {
  }

  @Override
  public String toString() {
    return getLookupString();
  }

  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(getLookupString());
  }

  @Nullable
  public <T> T as(Class<T> aClass) {
    //noinspection unchecked
    return aClass.isInstance(this) ? (T) this : null;
  }
  
  public boolean isCaseSensitive() {
    return true;
  }
}
