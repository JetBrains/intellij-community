/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.peer.PeerFactory;

public abstract class ProcessProxyFactory {
  public static ProcessProxyFactory getInstance() {
    return PeerFactory.getInstance().getProcessProxyFactory();
  }


  public abstract ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException;

  public abstract ProcessProxy getAttachedProxy(ProcessHandler processHandler);
}