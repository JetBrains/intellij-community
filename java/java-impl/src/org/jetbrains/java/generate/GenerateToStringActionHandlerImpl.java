// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate;

import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;

public final class GenerateToStringActionHandlerImpl extends GenerateToStringActionHandlerBase {
  public GenerateToStringActionHandlerImpl() {
    super(ToStringTemplatesManager.getInstance());
  }
}