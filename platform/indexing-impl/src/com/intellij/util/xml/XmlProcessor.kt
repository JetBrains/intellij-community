// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XmlProcessorUtil")

package com.intellij.util.xml

import com.fasterxml.aalto.WFCException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.text.CharSequenceReader
import com.intellij.util.xml.dom.createXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.NonNls
import java.io.InputStream
import java.io.Reader
import java.util.concurrent.CancellationException
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException

@Experimental
interface XmlProcessor {
  /**
   * Handles the start of an XML element.
   *
   * @param elementName The name of the element.
   * @param elementNamespacePrefix The namespace prefix of the element. Can be null.
   * @param elementNamespaceUri The namespace URI of the element. Can be null.
   * @return Returns `true` to continue processing, `false` to stop.
   */
  fun startElement(elementName: String,
                   elementNamespacePrefix: @NonNls String?,
                   elementNamespaceUri: @NonNls String?,
                   attributeIterator: AttributeIterator): Boolean

  /**
   * Handles the end of an XML element.
   *
   * @param elementName The name of the element.
   * @param elementNamespaceUri The namespace URI of the element. Can be null.
   * @return Returns `true` to continue processing, `false` to stop.
   */
  fun endElement(elementName: String, elementNamespaceUri: String?): Boolean = true

  /**
   * This `CharArray` should be treated as read-only and transient.
   * In other words, the array will contain the text characters until the `XMLStreamReader` moves on to the next event.
   * Any attempts to hold onto the character array beyond that time or modify the contents of the array
   * are breaches of the contract for this interface.
   */
  fun content(chars: CharArray, start: Int, end: Int) {
  }
}

private val LOG: Logger
  get() = logger<NanoXmlUtil>()

fun parseXml(reader: Reader, processor: XmlProcessor) {
  processXml(processor, reader) {
    createXmlStreamReader(reader)
  }
}

fun parseXml(inputStream: InputStream, processor: XmlProcessor) {
  processXml(processor, inputStream) {
    createXmlStreamReader(inputStream)
  }
}

fun parseXmlHeader(file: VirtualFile): XmlFileHeader {
  file.getInputStream().use {
    return parseXmlHeader(createXmlStreamReader(it))
  }
}

fun parseXmlHeader(reader: Reader): XmlFileHeader {
  reader.use {
    return parseXmlHeader(createXmlStreamReader(reader))
  }
}

fun parseXmlHeader(file: PsiFile): XmlFileHeader {
  return parseXmlHeader(CharSequenceReader(file.getViewProvider().getContents()))
}

private fun parseXmlHeader(reader: XMLStreamReader2): XmlFileHeader {
  try {
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamConstants.START_ELEMENT) {
        val location = reader.location
        return XmlFileHeader(reader.localName, reader.namespaceURI, location.publicId, location.systemId)
      }
    }
  }
  catch (e: XMLStreamException) {
    logOrRethrow(e)
  }
  catch (e: WFCException) {
    LOG.debug(e)
  }
  finally {
    reader.close()
  }

  return XmlFileHeader(null, null, null, null)
}

private fun logOrRethrow(e: XMLStreamException) {
  when (val nestedException = e.nestedException) {
    is ProcessCanceledException -> throw ProcessCanceledException(e)
    is CancellationException -> throw nestedException
    else -> LOG.debug(e)
  }
}

@Experimental
interface AttributeIterator {
  fun next(): Boolean

  fun localName(): String

  fun value(): String

  fun valueAsInt(): Int

  fun namespacePrefix(): String?

  fun namespaceUri(): String?
}

private class AttributeIteratorImpl(private val reader: XMLStreamReader2) : AttributeIterator {
  private var index = -1
  private var count = 0

  fun reset() {
    index = -1
    count = reader.attributeCount
  }

  override fun next(): Boolean {
    index++
    return index < count
  }

  override fun localName(): String = reader.getAttributeLocalName(index)

  override fun value(): String = reader.getAttributeValue(index)

  override fun valueAsInt(): Int = reader.elementAsInt

  override fun namespacePrefix(): String? = reader.getAttributePrefix(index)

  override fun namespaceUri(): String? = reader.getAttributeNamespace(index)
}

private inline fun processXml(processor: XmlProcessor, closeable: AutoCloseable, readerSupplier: () -> XMLStreamReader2) {
  try {
    closeable.use {
      val reader = readerSupplier()
      try {
        processXml(reader, processor)
      }
      catch (e: XMLStreamException) {
        logOrRethrow(e)
      }
      catch (e: WFCException) {
        LOG.debug(e)
      }
      finally {
        reader.close()
      }
    }
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    LOG.error(e)
  }
}

private fun processXml(reader: XMLStreamReader2, processor: XmlProcessor): Boolean {
  val attributeIterator = AttributeIteratorImpl(reader)
  while (reader.hasNext()) {
    val token = reader.next()
    when (token) {
      XMLStreamConstants.START_ELEMENT -> {
        val elementLocalName = reader.localName
        val elementNamespacePrefix = reader.prefix
        val elementNamespaceUri = reader.namespaceURI
        attributeIterator.reset()
        if (!processor.startElement(elementLocalName, elementNamespacePrefix, elementNamespaceUri, attributeIterator)) {
          return false
        }
      }
      XMLStreamConstants.CDATA -> {
        val fromIndex = reader.textStart
        processor.content(reader.textCharacters, fromIndex, fromIndex + reader.textLength)
      }
      XMLStreamConstants.END_ELEMENT -> {
        if (!processor.endElement(reader.localName, reader.namespaceURI)) {
          return false
        }
      }
      XMLStreamConstants.SPACE, XMLStreamConstants.CHARACTERS -> {
        if (!reader.isWhiteSpace) {
          val fromIndex = reader.textStart
          processor.content(reader.textCharacters, fromIndex, fromIndex + reader.textLength)
        }
      }
    }
  }
  return true
}