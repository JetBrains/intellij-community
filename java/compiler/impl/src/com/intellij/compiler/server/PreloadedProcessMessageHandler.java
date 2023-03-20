// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server;

/**
* @author Eugene Zhuravlev
*/
final class PreloadedProcessMessageHandler extends DelegatingMessageHandler {
  private volatile BuilderMessageHandler myDelegateHandler;

  PreloadedProcessMessageHandler() {
  }

  @Override
  protected BuilderMessageHandler getDelegateHandler() {
    final BuilderMessageHandler delegate = myDelegateHandler;
    return delegate != null? delegate : BuilderMessageHandler.DEAF;
  }

  public void setDelegateHandler(BuilderMessageHandler delegateHandler) {
    myDelegateHandler = delegateHandler;
  }
}
