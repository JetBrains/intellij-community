package com.intellij.configurationStore

import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser

internal inline fun lazyPreloadScheme(bytes: ByteArray, isUseOldFileNameSanitize: Boolean, consumer: (name: String?, parser: XmlPullParser) -> Unit) {
  val parser = MXParser()
  parser.setInput(bytes.inputStream().reader())
  consumer(preload(isUseOldFileNameSanitize, parser), parser)
}

private fun preload(isUseOldFileNameSanitize: Boolean, parser: MXParser): String? {
  var eventType = parser.eventType

  fun findName(): String? {
    eventType = parser.next()
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          if (parser.name == "option" && parser.getAttributeValue(null, "name") == "myName") {
            return parser.getAttributeValue(null, "value")
          }
        }
      }

      eventType = parser.next()
    }
    return null
  }

  do {
    when (eventType) {
      XmlPullParser.START_TAG -> {
        if (!isUseOldFileNameSanitize || parser.name != "component") {
          if (parser.name == "profile" || (isUseOldFileNameSanitize && parser.name == "copyright")) {
            return findName()
          }
          else if (parser.name == "inspections") {
            // backward compatibility - we don't write PROFILE_NAME_TAG anymore
            return parser.getAttributeValue(null, "profile_name") ?: findName()
          }
          else {
            return null
          }
        }
      }
    }
    eventType = parser.next()
  }
  while (eventType != XmlPullParser.END_DOCUMENT)
  return null
}