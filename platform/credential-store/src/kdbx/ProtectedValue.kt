// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore.kdbx

import com.intellij.configurationStore.JbXmlOutputter
import com.intellij.credentialStore.OneTimeString
import org.bouncycastle.crypto.SkippingStreamCipher
import org.jdom.Element
import org.jdom.Text
import java.io.Writer
import java.util.*

internal interface SecureString {
  fun get(clearable: Boolean = true): OneTimeString
}

internal class ProtectedValue(private var encryptedValue: ByteArray,
                              private var position: Int,
                              private var streamCipher: SkippingStreamCipher) : Text(), SecureString {
  @Synchronized
  override fun get(clearable: Boolean): OneTimeString {
    val output = ByteArray(encryptedValue.size)
    decryptInto(output)
    return OneTimeString(output, clearable = clearable)
  }

  @Synchronized
  fun setNewStreamCipher(newStreamCipher: SkippingStreamCipher) {
    val value = encryptedValue
    decryptInto(value)

    synchronized(newStreamCipher) {
      position = newStreamCipher.position.toInt()
      newStreamCipher.processBytes(value, 0, value.size, value, 0)
    }
    streamCipher = newStreamCipher
  }

  @Synchronized
  private fun decryptInto(out: ByteArray) {
    synchronized(streamCipher) {
      streamCipher.seekTo(position.toLong())
      streamCipher.processBytes(encryptedValue, 0, encryptedValue.size, out, 0)
    }
  }

  override fun getText() = throw IllegalStateException("encodeToBase64 must be used for serialization")

  fun encodeToBase64(): String {
    return when {
      encryptedValue.isEmpty() -> ""
      else -> Base64.getEncoder().encodeToString(encryptedValue)
    }
  }
}

internal class UnsavedProtectedValue(val secureString: StringProtectedByStreamCipher) : Text(), SecureString by secureString {
  override fun getText() = throw IllegalStateException("Must be converted to ProtectedValue for serialization")
}

internal class ProtectedXmlWriter(private val streamCipher: SkippingStreamCipher) : JbXmlOutputter(isForbidSensitiveData = false) {
  override fun writeContent(out: Writer, element: Element, level: Int): Boolean {
    if (element.name != KdbxEntryElementNames.value) {
      return super.writeContent(out, element, level)
    }

    val value = element.content.firstOrNull()
    if (value is SecureString) {
      val protectedValue: ProtectedValue
      if (value is ProtectedValue) {
        value.setNewStreamCipher(streamCipher)
        protectedValue = value
      }
      else {
        val bytes = (value as UnsavedProtectedValue).secureString.getAsByteArray()
        synchronized(streamCipher) {
          val position = streamCipher.position.toInt()
          streamCipher.processBytes(bytes, 0, bytes.size, bytes, 0)
          protectedValue = ProtectedValue(bytes, position, streamCipher)
        }
        element.setContent(protectedValue)
      }

      out.write('>'.code)
      out.write(escapeElementEntities(protectedValue.encodeToBase64()))
      return true
    }

    return super.writeContent(out, element, level)
  }
}

internal fun isValueProtected(valueElement: Element): Boolean {
  return valueElement.getAttributeValue(KdbxAttributeNames.protected).equals("true", ignoreCase = true)
}

internal class XmlProtectedValueTransformer(private val streamCipher: SkippingStreamCipher) {
  private var position = 0

  fun processEntries(parentElement: Element) {
    // we must process in exact order
    for (element in parentElement.content) {
      if (element !is Element) {
        continue
      }

      if (element.name == KdbxDbElementNames.group) {
        processEntries(element)
      }
      else if (element.name == KdbxDbElementNames.entry) {
        for (container in element.getChildren(KdbxEntryElementNames.string)) {
          val valueElement = container.getChild(KdbxEntryElementNames.value) ?: continue
          if (isValueProtected(valueElement)) {
            val value = Base64.getDecoder().decode(valueElement.text)
            valueElement.setContent(ProtectedValue(value, position, streamCipher))
            position += value.size
          }
        }
      }
    }
  }
}