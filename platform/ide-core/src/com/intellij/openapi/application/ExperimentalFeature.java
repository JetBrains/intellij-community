// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nls;

/**
 * @author Konstantin Bulenkov
 * @deprecated Please use the registry to enable/disable experimental features.
 * This extension can only be used for A/B tests (with percentOfUsers not equal to either 0 or 100).
 */
@Deprecated
public abstract class ExperimentalFeature {
  @Attribute("id")
  public String id;

  @Attribute("percentOfUsers")
  public int percentOfUsers = 0;

  @Attribute("internalFeature")
  public boolean internalFeature = false;

  @Tag("description")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String description;

  @Attribute("requireRestart")
  public boolean requireRestart = false;

  public abstract boolean isEnabled();
}
