// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Experimental
public interface InlineDocumentation {

  /**
   * The returned range might span over several elements,
   * the range can start or end in a middle of a PsiElement.
   *
   * @return absolute range of the documentation, e.g. JavaDoc element of a Java class
   */
  @NotNull TextRange getDocumentationRange();

  /**
   * The returned range is used to find inline documentation by an offset outside of {@link #getDocumentationRange}.
   *
   * @return absolute range of the documentation owner, e.g. Java class,
   * or {@code null} if there is no owner
   */
  @Nullable TextRange getDocumentationOwnerRange();

  @RequiresReadLock
  @RequiresBackgroundThread
  @Nls @Nullable String renderText();

  /**
   * @return the target which represents the owner of this inline documentation, e.g. documentation target for a class,
   * or {@code null} if there is no owner
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @Nullable DocumentationTarget getOwnerTarget();
}
