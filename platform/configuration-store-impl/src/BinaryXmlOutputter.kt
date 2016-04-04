package com.intellij.configurationStore

import com.intellij.util.containers.isNullOrEmpty
import org.jdom.*
import java.io.DataOutputStream

private enum class TypeMarker {
  ELEMENT, CDATA, TEXT, ELEMENT_END
}

fun output(doc: Document, out: DataOutputStream) {
  val content = doc.content
  val size = content.size
  for (i in 0..size - 1) {
    val obj = content[i]
    when (obj) {
      is Element -> printElement(out, doc.rootElement)
    }
  }

  out.flush()
}

fun writeElement(element: Element, out: DataOutputStream) {
  printElement(out, element)
  out.flush()
}

private fun printElement(out: DataOutputStream, element: Element) {
  out.writeByte(TypeMarker.ELEMENT.ordinal)
  out.writeUTF(element.name)

  val content = element.content
  printAttributes(out, element.attributes)

  for (item in content) {
    if (item is Element) {
      printElement(out, item)
    }
    else if (item is Text) {
      if (!isAllWhitespace(item)) {
        out.writeByte(TypeMarker.TEXT.ordinal)
        out.writeUTF(item.text)
      }
    }
    else if (item is CDATA) {
      out.writeByte(TypeMarker.CDATA.ordinal)
      out.writeUTF(item.text)
    }
  }
  out.writeByte(TypeMarker.ELEMENT_END.ordinal)
}

private fun printAttributes(out: DataOutputStream, attributes: List<Attribute>?) {
  if (attributes.isNullOrEmpty()) {
    val size = attributes?.size ?: 0
    if (size > 255) {
      throw UnsupportedOperationException("attributes size > 255")
    }
    out.writeByte(size)
    return
  }

  for (attribute in attributes!!) {
    out.writeUTF(attribute.name)
    out.writeUTF(attribute.value)
  }
}

private fun isAllWhitespace(obj: Content): Boolean {
  val str = (obj as? Text)?.text ?: return false
  for (i in 0..str.length - 1) {
    if (!Verifier.isXMLWhitespace(str[i])) {
      return false
    }
  }
  return true
}