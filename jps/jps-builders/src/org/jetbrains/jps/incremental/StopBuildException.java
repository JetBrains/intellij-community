// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.Nls;

/**
 * This exception indicates that build should be stopped.
 * It is assumed that all necessary messages have been reported already, so optional message passed toi constructor 
 * is treated as build progress message
 * 
 * @author Eugene Zhuravlev
 */
public final class StopBuildException extends ProjectBuildException{
  public StopBuildException() {
    this(null);
  }

  public StopBuildException(@Nls(capitalization = Nls.Capitalization.Sentence) String infoMessage) {
    super(infoMessage);
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
