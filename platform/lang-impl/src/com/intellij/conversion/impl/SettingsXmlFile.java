// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.SystemProperties;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

final class SettingsXmlFile {
  private final Path myFile;
  private final Document myDocument;
  private final Element myRootElement;

  SettingsXmlFile(@NotNull Path file) throws CannotConvertException {
    myFile = file;
    Document result;
    try {
      //noinspection deprecation
      result = JDOMUtil.loadDocument(CharsetToolkit.inputStreamSkippingBOM(new BufferedInputStream(Files.newInputStream(file))));
    }
    catch (JDOMException | IOException e) {
      throw new CannotConvertException(file + ": " + e.getMessage(), e);
    }
    myDocument = result;
    myRootElement = myDocument.getRootElement();
  }

  public @NotNull Path getFile() {
    return myFile;
  }

  public @NotNull Element getRootElement() {
    return myRootElement;
  }

  public void save() throws IOException {
    // directory must already exists as we already read the file
    try (BufferedWriter writer = Files.newBufferedWriter(myFile)) {
      JDOMUtil.writeDocument(myDocument, writer, SystemProperties.getLineSeparator());
    }
  }

  @Nullable
  public Element findComponent(String componentName) {
    return JDomSerializationUtil.findComponent(myRootElement, componentName);
  }
}
