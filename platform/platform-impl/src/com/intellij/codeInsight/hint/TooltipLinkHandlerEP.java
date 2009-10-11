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
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  public final String getDescription(@NotNull String description, Editor editor) {
    if (description.startsWith(prefix)) {
      return myHandler.getValue().getDescription(description.substring(prefix.length()), editor);
    }
    return null;
  }
}
