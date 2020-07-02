// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ComponentManagerSettings;
import com.intellij.conversion.XmlBasedSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class ComponentManagerSettingsImpl implements ComponentManagerSettings, XmlBasedSettings {
  protected final SettingsXmlFile mySettingsFile;
  protected final ConversionContextImpl myContext;

  protected ComponentManagerSettingsImpl(@NotNull Path file, @NotNull ConversionContextImpl context) throws CannotConvertException {
    myContext = context;
    mySettingsFile = context.getOrCreateFile(file);
  }

  @Override
  public Element getComponentElement(@NotNull @NonNls String componentName) {
    return mySettingsFile.findComponent(componentName);
  }

  @Override
  @NotNull
  public Element getRootElement() {
    return mySettingsFile.getRootElement();
  }

  @Override
  public @NotNull Path getPath() {
    return mySettingsFile.getFile();
  }
}
