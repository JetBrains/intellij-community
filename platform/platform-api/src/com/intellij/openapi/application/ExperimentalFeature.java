// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nls;

/**
 * @author Konstantin Bulenkov
 */
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
