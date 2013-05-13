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

package com.intellij.codeInsight.daemon;

import org.jetbrains.annotations.NotNull;

/**
 * Implement this in your {@link com.intellij.psi.PsiReference} to provide custom error message.
 */
public interface EmptyResolveMessageProvider {

  /**
   * Returns custom unresolved message pattern. First, returned value is used as pattern in <code>MessageFormat.format()</code> call.
   * If the call fails, returned value is used as is.
   * @return pattern or message
   * @see XmlHighlightVisitor#getErrorDescription()
   */
  @NotNull
  String getUnresolvedMessagePattern();
}
