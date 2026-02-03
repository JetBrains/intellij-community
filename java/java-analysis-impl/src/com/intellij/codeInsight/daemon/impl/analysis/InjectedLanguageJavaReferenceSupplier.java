// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class InjectedLanguageJavaReferenceSupplier {
  public static final ExtensionPointName<InjectedLanguageJavaReferenceSupplier> EP_NAME =
    ExtensionPointName.create("com.intellij.injectedLanguageJavaReferenceSupplier");

  /**
   * Language ID.
   */
  @Attribute("language")
  @RequiredElement
  public String language;

  public static boolean containsPsiMemberReferences(@NotNull String languageId) {
    for (InjectedLanguageJavaReferenceSupplier handler : EP_NAME.getExtensionList()) {
      if (languageId.equals(handler.language)) return true;
    }
    return false;
  }
}
