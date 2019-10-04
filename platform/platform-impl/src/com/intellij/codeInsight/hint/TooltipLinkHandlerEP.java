// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public final class TooltipLinkHandlerEP extends BaseKeyedLazyInstance<TooltipLinkHandler> {
  public static final ExtensionPointName<TooltipLinkHandlerEP> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.linkHandler");

  @Attribute("prefix")
  public String prefix;

  @Attribute("handlerClass")
  public String handlerClassName;

  @Nullable
  @Override
  protected String getImplementationClassName() {
    return handlerClassName;
  }

  public static boolean handleLink(@NotNull final String ref, @NotNull final Editor editor) {
    for (TooltipLinkHandlerEP handlerEP : EP_NAME.getIterable()) {
      if (ref.startsWith(handlerEP.prefix)) {
        final String refSuffix = ref.substring(handlerEP.prefix.length());
        return handlerEP.getInstance().handleLink(refSuffix.replaceAll("<br/>", "\n"), editor);
      }
    }
    return false;
  }

  @Nullable
  public static String getDescription(@NotNull final String ref, @NotNull final Editor editor) {
    for (final TooltipLinkHandlerEP handlerEP : EP_NAME.getIterable()) {
      if (ref.startsWith(handlerEP.prefix)) {
        final String refSuffix = ref.substring(handlerEP.prefix.length());
        return handlerEP.getInstance().getDescription(refSuffix, editor);
      }
    }
    return null;
  }

  @NotNull
  public static String getDescriptionTitle(@NotNull String ref, @NotNull Editor editor) {
    for (final TooltipLinkHandlerEP handlerEP : EP_NAME.getIterable()) {
      if (ref.startsWith(handlerEP.prefix)) {
        final String refSuffix = ref.substring(handlerEP.prefix.length());
        return handlerEP.getInstance().getDescriptionTitle(refSuffix, editor);
      }
    }
    return TooltipLinkHandler.INSPECTION_INFO;
  }
}
