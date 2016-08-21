/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.credentialStore.kdbx

import com.intellij.util.inputStream
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

internal fun loadKdbx(file: Path, credentials: KeePassCredentials) = file.inputStream().use {
  val db = KeePassDatabase()
  db.load(KdbxStreamFormat(), credentials, it)
  db
}

class KdbxPassword(password: ByteArray) : KeePassCredentials {
  override val key: ByteArray

  init {
    val md = MessageDigest.getInstance("SHA-256")
    key = md.digest(md.digest(password))
  }
}

@Suppress("unused")
class KdbxKeyFile(password: ByteArray, inputStream: InputStream) : KeePassCredentials {
  override val key: ByteArray

  init {
    val md = MessageDigest.getInstance("SHA-256")
    val pwKey = md.digest(password)
    md.update(pwKey)
    key = md.digest(loadKdbxKeyFile(inputStream) ?: throw IllegalStateException("Could not read key file"))
  }
}

fun loadKdbxKeyFile(inputStream: InputStream): ByteArray? {
  val base64: String?
  try {
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = documentBuilder.parse(inputStream)
    base64 = XPathFactory.newInstance().newXPath().evaluate("//KeyFile/Key/Data/text()", doc, XPathConstants.STRING) as String?
    if (base64 == null) {
      return null
    }
  }
  catch (e: Exception) {
    return null
  }

  return DatatypeConverter.parseBase64Binary(base64)
}

interface KeePassCredentials {
  val key: ByteArray
}