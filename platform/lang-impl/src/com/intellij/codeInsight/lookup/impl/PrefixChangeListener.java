/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

/**
 * Listener for prefix changes in {@link LookupImpl}.
 *
 * @see LookupImpl#addPrefixChangeListener
 */
public interface PrefixChangeListener {

  /** called before the lookup prefix is truncated */
  default void beforeTruncate() { }

  /** called after the lookup prefix has been truncated */
  default void afterTruncate() { }

  /** called before {@code c} is appended to the lookup prefix */
  default void beforeAppend(char c) { }

  /** called after {@code c} has been appended to the lookup prefix */
  default void afterAppend(char c) { }
}
