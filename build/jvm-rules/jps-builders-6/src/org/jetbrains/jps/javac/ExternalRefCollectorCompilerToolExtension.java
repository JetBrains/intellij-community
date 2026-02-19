// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

public final class ExternalRefCollectorCompilerToolExtension extends AbstractRefCollectorCompilerToolExtension {
  public static final String ENABLED_PARAM = "external.java.process.ref.collector.enabled";

  @Override
  protected boolean isEnabled() {
    return "true".equals(System.getProperty(ENABLED_PARAM));
  }

  public static void enable() {
    System.setProperty(ENABLED_PARAM, "true");
  }

  public static void disable() {
    System.clearProperty(ENABLED_PARAM);
  }
}
