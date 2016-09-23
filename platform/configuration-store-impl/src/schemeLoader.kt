package com.intellij.configurationStore

import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser

internal inline fun lazyPreloadScheme(bytes: ByteArray, isUseOldFileNameSanitize: Boolean, consumer: (name: String?, parser: XmlPullParser) -> Unit) {
  val parser = MXParser()
  parser.setInput(bytes.inputStream().reader())
  var eventType = parser.eventType
  read@ do {
    when (eventType) {
      XmlPullParser.START_TAG -> {
        if (!isUseOldFileNameSanitize || parser.name != "component") {
          var name: String? = null
          if (isUseOldFileNameSanitize && (parser.name == "profile" || parser.name == "copyright")) {
            eventType = parser.next()
            findName@ while (eventType != XmlPullParser.END_DOCUMENT) {
              when (eventType) {
                XmlPullParser.START_TAG -> {
                  if (parser.name == "option" && parser.getAttributeValue(null, "name") == "myName") {
                    name = parser.getAttributeValue(null, "value")
                    break@findName
                  }
                }
              }

              eventType = parser.next()
            }
          }

          consumer(name, parser)
          break@read
        }
      }
    }
    eventType = parser.next()
  }
  while (eventType != XmlPullParser.END_DOCUMENT)
}