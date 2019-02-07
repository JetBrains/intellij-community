// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import net.n3.nanoxml.IXMLBuilder;

import java.io.Reader;

public interface NanoXmlBuilder extends IXMLBuilder {
  @Override
  default void startBuilding(String s, int i) throws Exception {
  }

  @Override
  default void newProcessingInstruction(String s, Reader reader) throws Exception {
  }

  @Override
  default void startElement(String s, String s1, String s2, String s3, int i) throws Exception {
  }

  @Override
  default void addAttribute(String s, String s1, String s2, String s3, String s4) throws Exception {
  }

  @Override
  default void elementAttributesProcessed(String s, String s1, String s2) throws Exception {
  }

  @Override
  default void endElement(String s, String s1, String s2) throws Exception {
  }

  @Override
  default void addPCData(Reader reader, String s, int i) throws Exception {
  }

  @Override
  default Object getResult() throws Exception {
    return null;
  }

  static void stop() throws NanoXmlUtil.ParserStoppedXmlException {
    throw NanoXmlUtil.ParserStoppedXmlException.INSTANCE;
  }
}
