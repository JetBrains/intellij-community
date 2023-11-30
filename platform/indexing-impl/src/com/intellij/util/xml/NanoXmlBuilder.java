// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import net.n3.nanoxml.IXMLBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;

@SuppressWarnings("RedundantThrows")
public @NonNls interface NanoXmlBuilder extends IXMLBuilder {
  @Override
  default void startBuilding(String systemID, int lineNr) throws Exception { }

  @Override
  default void newProcessingInstruction(String target, Reader reader) throws Exception { }

  @Override
  default void startElement(String name, @Nullable String nsPrefix, @Nullable String nsSystemID, String systemID, int lineNr) throws Exception { }

  @Override
  default void addAttribute(String key, @Nullable String nsPrefix, @Nullable String nsSystemID, String value, String type) throws Exception { }

  @Override
  default void elementAttributesProcessed(String name, @Nullable String nsPrefix, @Nullable String nsSystemID) throws Exception { }

  @Override
  default void endElement(String name, @Nullable String nsPrefix, @Nullable String nsSystemID) throws Exception { }

  @Override
  default void addPCData(Reader reader, String systemID, int lineNr) throws Exception { }

  @Override
  default Object getResult() throws Exception {
    return null;
  }

  static void stop() throws NanoXmlUtil.ParserStoppedXmlException {
    throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
  }
}