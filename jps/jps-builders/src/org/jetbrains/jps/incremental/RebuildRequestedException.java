// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

/**
 * @author Eugene Zhuravlev
 */
public final class RebuildRequestedException extends ProjectBuildException{

  public RebuildRequestedException(Throwable cause) {
    super(cause);
  }

  @Override
  public Throwable fillInStackTrace() {
    return this;
  }
}
