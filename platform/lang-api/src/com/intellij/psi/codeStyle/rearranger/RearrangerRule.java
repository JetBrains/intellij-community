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
package com.intellij.psi.codeStyle.rearranger;

import org.jetbrains.annotations.NotNull;

/**
 * Identifies a strategy which can be used for grouping {@link RearrangerEntry entries}.
 * <p/>
 * Example: we can define a rule like 'private final non-static fields' or 'public static methods' etc.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/17/12 11:07 AM
 */
public interface RearrangerRule {

  /**
   * Allows to check if given entry is matched by the current rule.
   * <p/>
   * Example: entry like 'public final field' should be matched by a 'final fields' rule but not matched by a 'private fields' rule.
   * 
   * @param entry  entry to check
   * @return       <code>true</code> if given entry is matched by the current rule; <code>false</code> otherwise
   */
  boolean isMatched(@NotNull RearrangerEntry entry);
}
