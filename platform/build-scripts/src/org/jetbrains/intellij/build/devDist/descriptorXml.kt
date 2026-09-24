// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import com.intellij.util.xml.dom.createXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLStreamConstants

/**
 * Whether the root element of the descriptor [xml] has the attribute [name].
 *
 * A plan asks this for every content module of every plugin. The reader stops at the root start tag, so the question
 * costs the prolog and one tag, and not the whole document. The attribute is matched by its local name, as the light
 * XML model matches it.
 */
@ApiStatus.Internal
fun hasRootXmlAttribute(xml: String, name: String): Boolean {
  return hasRootAttribute(createXmlStreamReader(StringReader(xml)), name)
}

/** [hasRootXmlAttribute] over the descriptor [file]. */
@ApiStatus.Internal
fun hasRootXmlAttribute(file: Path, name: String): Boolean {
  return hasRootAttribute(createXmlStreamReader(Files.newInputStream(file), file.toString()), name)
}

private fun hasRootAttribute(reader: XMLStreamReader2, name: String): Boolean {
  try {
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamConstants.START_ELEMENT) {
        for (i in 0 until reader.attributeCount) {
          if (reader.getAttributeLocalName(i) == name) {
            return true
          }
        }
        return false
      }
    }
    return false
  }
  finally {
    reader.closeCompletely()
  }
}
