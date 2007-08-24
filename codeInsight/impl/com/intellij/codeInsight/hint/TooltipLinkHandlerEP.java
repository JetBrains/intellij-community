/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.hint;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
    protected Class<TooltipLinkHandler> getInstanceClass() throws ClassNotFoundException {
      return findClass(handlerClassName);
    }
  };

  public final boolean handleLink(@NotNull String description, @NotNull final Editor editor, @NotNull final JEditorPane hintComponent) {
    if (description.startsWith(prefix)) {
      myHandler.getValue().handleLink(description.substring(prefix.length()).replaceAll("\\<br\\>", "\n"), editor, hintComponent);
      return true;
    }
    return false;
  }
}
