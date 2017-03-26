/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.psi;

import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.Nullable;

public interface RegExpGroup extends RegExpAtom, PsiNamedElement {

  boolean isCapturing();

  @Nullable
  RegExpPattern getPattern();

  /** @deprecated use #getType() */
  @Deprecated
  boolean isPythonNamedGroup();

  /** @deprecated use #getType() */
  @Deprecated
  boolean isRubyNamedGroup();

  /** @return true, if this is a named group of any kind, false otherwise */
  boolean isAnyNamedGroup();

  @Nullable
  String getGroupName();

  Type getType();

  enum Type {
    /** (?<name>pattern) */
    NAMED_GROUP,

    /** (?'name'pattern) */
    QUOTED_NAMED_GROUP,

    /** (?P<name>pattern) */
    PYTHON_NAMED_GROUP,

    /** (pattern) */
    CAPTURING_GROUP,

    /** (?>pattern) */
    ATOMIC,

    /** (?:pattern) */
    NON_CAPTURING,

    /** (?=pattern) */
    POSITIVE_LOOKAHEAD,

    /** (?!pattern) */
    NEGATIVE_LOOKAHEAD,

    /** (?<=pattern) */
    POSITIVE_LOOKBEHIND,

    /** (?<!pattern) */
    NEGATIVE_LOOKBEHIND,

    /** (?i:pattern) */
    OPTIONS
  }
}
