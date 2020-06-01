// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.serviceContainer.LazyExtensionInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@ApiStatus.Experimental
public class CompletionIgnoreDumbnessEP extends LazyExtensionInstance<Object> {
  private static final ExtensionPointName<CompletionIgnoreDumbnessEP> EP_NAME =
    ExtensionPointName.create("com.intellij.completion.ignoringDumbnessAllowed");

  public static boolean isIgnoringDumbnessAllowed(@NotNull Language language) {
    return EP_NAME.getByKey(language.getID(), CompletionIgnoreDumbnessEP.class, ep -> ep.language) != null;
  }

  /**
   * Language ID.
   *
   * @see Language#getID()
   */
  @Attribute("language")
  public String language;

  @Override
  protected @Nullable String getImplementationClassName() {
    return null;
  }
}
