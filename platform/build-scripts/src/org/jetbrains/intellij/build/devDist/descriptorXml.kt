// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.annotations.ApiStatus
import java.io.StringReader
import java.nio.file.Path

/**
 * Whether the root element of the descriptor [xml] has the attribute [name].
 *
 * A plan asks this for every content module of every plugin. The light XML model answers it, and a JDOM tree with
 * its name checks and namespaces costs more than the rest of the question.
 */
@ApiStatus.Internal
fun hasRootXmlAttribute(xml: String, name: String): Boolean {
  return readXmlAsModel(StringReader(xml)).attributes.containsKey(name)
}

/** [hasRootXmlAttribute] over the descriptor [file]. */
@ApiStatus.Internal
fun hasRootXmlAttribute(file: Path, name: String): Boolean {
  return readXmlAsModel(file).attributes.containsKey(name)
}
