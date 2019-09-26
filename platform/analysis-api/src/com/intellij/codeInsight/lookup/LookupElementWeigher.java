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
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class LookupElementWeigher {
  private final String myId;
  private final boolean myNegated;
  private final boolean myPrefixDependent;

  protected LookupElementWeigher(String id, boolean negated, boolean dependsOnPrefix) {
    myId = id;
    myNegated = negated;
    myPrefixDependent = dependsOnPrefix;
  }

  protected LookupElementWeigher(String id) {
    this(id, false, false);
  }

  public boolean isPrefixDependent() {
    return myPrefixDependent;
  }

  public boolean isNegated() {
    return myNegated;
  }

  @Override
  public String toString() {
    return myId;
  }

  @Nullable
  public Comparable weigh(@NotNull LookupElement element, @NotNull WeighingContext context) {
    return weigh(element);
  }

  @Nullable
  public Comparable weigh(@NotNull LookupElement element) {
    throw new UnsupportedOperationException("weigh not implemented in " + getClass());
  }

}
