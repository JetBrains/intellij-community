// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import com.intellij.openapi.diagnostic.ControlFlowException;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

/**
 * Throw in an extension class constructor to mark the extension as not applicable.
 */
public final class ExtensionNotApplicableException extends RuntimeException implements ControlFlowException {
  /**
   * @deprecated Use {@link #create()}
   */
  @Deprecated
  public static final ExtensionNotApplicableException INSTANCE = new ExtensionNotApplicableException(false);

  private static Supplier<ExtensionNotApplicableException> factory;

  static {
    if (System.getenv("TEAMCITY_VERSION") == null) {
      factory = () -> INSTANCE;
    }
    else {
      useFactoryWithStacktrace();
    }
  }

  public static ExtensionNotApplicableException create() {
    return factory.get();
  }

  @ApiStatus.Internal
  public static void useFactoryWithStacktrace() {
    factory = () -> new ExtensionNotApplicableException(true);
  }

  private ExtensionNotApplicableException(boolean withStacktrace) {
    super(null, null, false, withStacktrace);
  }
}
