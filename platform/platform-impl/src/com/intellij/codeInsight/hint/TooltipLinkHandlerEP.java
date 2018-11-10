// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class TooltipLinkHandlerEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<TooltipLinkHandlerEP> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.linkHandler");

  @Attribute("prefix")
  public String prefix;

  @Attribute("handlerClass")
  public String handlerClassName;

  private final LazyInstance<TooltipLinkHandler> myHandler = new LazyInstance<TooltipLinkHandler>() {
    @Override
    protected Class<TooltipLinkHandler> getInstanceClass() throws ClassNotFoundException {
      return findClass(handlerClassName);
    }
  };

  public static boolean handleLink(@NotNull final String ref, @NotNull final Editor editor) {
    for (final TooltipLinkHandlerEP handlerEP : EP_NAME.getExtensionList()) {
      if (ref.startsWith(handlerEP.prefix)) {
        final String refSuffix = ref.substring(handlerEP.prefix.length());
        return handlerEP.myHandler.getValue().handleLink(refSuffix.replaceAll("<br/>", "\n"), editor);
      }
    }
    return false;
  }

  @Nullable
  public static String getDescription(@NotNull final String ref, @NotNull final Editor editor) {
    for (final TooltipLinkHandlerEP handlerEP : EP_NAME.getExtensionList()) {
      if (ref.startsWith(handlerEP.prefix)) {
        final String refSuffix = ref.substring(handlerEP.prefix.length());
        return handlerEP.myHandler.getValue().getDescription(refSuffix, editor);
      }
    }
    return null;
  }
}
