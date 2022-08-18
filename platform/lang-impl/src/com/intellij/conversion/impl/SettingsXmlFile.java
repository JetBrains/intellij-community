// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ComponentManagerSettings;
import com.intellij.conversion.WorkspaceSettings;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

class SettingsXmlFile implements ComponentManagerSettings, WorkspaceSettings {
  private static final Element EMPTY_ELEMENT = new Element("root");

  private final Path file;
  // Document is used because XML prolog must be written as is
  private @Nullable Element element;

  SettingsXmlFile(@NotNull Path file) throws CannotConvertException {
    this.file = file;
  }

  public @NotNull Path getFile() {
    return file;
  }

  @Override
  public Element getComponentElement(@NotNull @NonNls String componentName) {
    return findComponent(componentName);
  }

  @Override
  public @NotNull Path getPath() {
    return file;
  }

  private @NotNull Element getElement() {
    Element result = element;
    if (result == null) {
      try {
        result = JDOMUtil.load(file);
      }
      catch (NoSuchFileException e) {
        result = EMPTY_ELEMENT;
      }
      catch (JDOMException | IOException e) {
        element = EMPTY_ELEMENT;
        throw new CannotConvertException("Cannot load " + file, e);
      }
      element = result;
    }
    return result;
  }

  @Override
  public @NotNull Element getRootElement() {
    return getElement();
  }

  public void save() throws IOException {
    if (element == null || element == EMPTY_ELEMENT) {
      return;
    }

    Files.createDirectories(file.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(file)) {
      JDOMUtil.writeElement(getElement(), writer, System.lineSeparator());
    }
  }

  public @Nullable Element findComponent(@NotNull String componentName) {
    return element == EMPTY_ELEMENT ? null : JDomSerializationUtil.findComponent(getElement(), componentName);
  }
}
