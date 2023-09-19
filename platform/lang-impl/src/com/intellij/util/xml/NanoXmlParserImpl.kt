// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.framework.detection.FileContentPattern;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;

public final class NanoXmlParserImpl implements FileContentPattern.ParseXml {
  @Override
  public @NotNull XmlFileHeader parseHeaderWithException(@NotNull Reader reader) {
    return NanoXmlUtil.parseHeaderWithException(reader);
  }
}
