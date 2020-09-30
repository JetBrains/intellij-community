// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

public class ExternalRefCollectorCompilerToolExtension extends AbstractRefCollectorCompilerToolExtension {
  public static final String ENABLED_PARAM = "external.java.process.ref.collector.enabled";

  @Override
  protected boolean isEnabled() {
    return "true".equals(System.getProperty(ENABLED_PARAM));
  }

}
