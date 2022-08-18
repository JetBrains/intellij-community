// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.errorTreeView;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class SimpleMessageElement extends ErrorTreeElement{
  private final String[] myMessage;
  private final Object myData;

  public SimpleMessageElement(@NotNull ErrorTreeElementKind kind, String[] text, Object data) {
    super(kind);
    myMessage = text;
    myData = data;
  }

  @Override
  public String[] getText() {
    return myMessage;
  }

  @Override
  public Object getData() {
    return myData;
  }

  @Override
  public String getExportTextPrefix() {
    return "";
  }
}
