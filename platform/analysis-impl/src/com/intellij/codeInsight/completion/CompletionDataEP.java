// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @deprecated see {@link CompletionContributor}
 * @author yole
 */
@Deprecated
public final class CompletionDataEP extends AbstractExtensionPointBean {
  // these must be public for scrambling compatibility
  @Attribute("fileType")
  public String fileType;
  @Attribute("className")
  public String className;

  private final NotNullLazyValue<CompletionData> myHandler = NotNullLazyValue.lazy(() -> {
    return instantiate(findExtensionClass(className), ApplicationManager.getApplication().getPicoContainer());
  });

  public CompletionData getHandler() {
    return myHandler.getValue();
  }
}