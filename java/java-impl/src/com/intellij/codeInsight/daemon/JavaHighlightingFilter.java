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
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * This filter is intended to suppress highlighting of certain errors which are allowed in Java-like files (e.g. JSP).
 * The filter should return true only if <b>absolutely sure</b> about context.
 *
 * todo[r.sh] add other kinds; use in JSP; move to API (?)
 */
public abstract class JavaHighlightingFilter {
  public static final ExtensionPointName<JavaHighlightingFilter> EP_NAME =
    ExtensionPointName.create("com.intellij.java.highlighting.filter");

  public enum Kind {
    REFERENCE, RETURN_STATEMENT
  }

  public abstract boolean isSuppressed(@NotNull final Kind kind, @NotNull final PsiElement element);

  public static boolean suppressed(@NotNull final Kind kind, @NotNull final PsiElement element) {
    for (JavaHighlightingFilter filter : EP_NAME.getExtensions()) {
      if (filter.isSuppressed(kind, element)) return true;
    }

    return false;
  }
}
