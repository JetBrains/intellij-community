// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml

import com.intellij.framework.detection.FileContentPattern
import java.io.Reader

private class NanoXmlParserImpl : FileContentPattern.ParseXml {
  override fun parseHeaderWithException(reader: Reader): XmlFileHeader {
    return NanoXmlUtil.parseHeaderWithException(reader)
  }
}
