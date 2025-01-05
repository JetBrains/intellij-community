// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java.impl;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.JpsJavaProjectExtension;
import org.jetbrains.jps.model.java.LanguageLevel;

import java.util.Objects;

final class JpsJavaProjectExtensionImpl extends JpsElementBase<JpsJavaProjectExtensionImpl> implements JpsJavaProjectExtension {
  private String myOutputUrl;
  private LanguageLevel myLanguageLevel;

  JpsJavaProjectExtensionImpl() {
  }

  private JpsJavaProjectExtensionImpl(JpsJavaProjectExtensionImpl original) {
    myOutputUrl = original.myOutputUrl;
    myLanguageLevel = original.myLanguageLevel;
  }

  @Override
  public @NotNull JpsJavaProjectExtensionImpl createCopy() {
    return new JpsJavaProjectExtensionImpl(this);
  }

  @Override
  public String getOutputUrl() {
    return myOutputUrl;
  }

  @Override
  public void setOutputUrl(String outputUrl) {
    if (!Objects.equals(myOutputUrl, outputUrl)) {
      myOutputUrl = outputUrl;
    }
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public void setLanguageLevel(LanguageLevel languageLevel) {
    if (!Comparing.equal(myLanguageLevel, languageLevel)) {
      myLanguageLevel = languageLevel;
    }
  }
}
