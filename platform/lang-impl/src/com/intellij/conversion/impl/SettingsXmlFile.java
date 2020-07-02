// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ComponentManagerSettings;
import com.intellij.conversion.WorkspaceSettings;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

class SettingsXmlFile implements ComponentManagerSettings, WorkspaceSettings {
  private static final Document EMPTY_DOCUMENT = new Document(new Element("root"));

  private final Path file;
  // Document is used because XML prolog must be written as is
  private @Nullable Document document;

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

  private @NotNull Document getDocument() {
    Document result = document;
    if (result == null) {
      try {
        //noinspection deprecation
        result = JDOMUtil.loadDocument(CharsetToolkit.inputStreamSkippingBOM(new BufferedInputStream(Files.newInputStream(file))));
      }
      catch (NoSuchFileException e) {
        result = EMPTY_DOCUMENT;
      }
      catch (JDOMException | IOException e) {
        document = EMPTY_DOCUMENT;
        throw new CannotConvertException("Cannot load " + file, e);
      }
      document = result;
    }
    return result;
  }

  @Override
  public @NotNull Element getRootElement() {
    return getDocument().getRootElement();
  }

  public void save() throws IOException {
    if (document == null || document == EMPTY_DOCUMENT) {
      return;
    }

    Files.createDirectories(file.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(file)) {
      JDOMUtil.writeDocument(getDocument(), writer, SystemProperties.getLineSeparator());
    }
  }

  public @Nullable Element findComponent(@NotNull String componentName) {
    return document == EMPTY_DOCUMENT ? null : JDomSerializationUtil.findComponent(getDocument().getRootElement(), componentName);
  }
}
