// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import fleet.util.toNSString
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSMutableCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.localizedCapitalizedString
import platform.Foundation.localizedLowercaseString
import platform.Foundation.localizedUppercaseString
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Foundation.stringByRemovingPercentEncoding

@Actual
fun String.capitalizeWithCurrentLocaleNative(): String =
  toNSString().localizedCapitalizedString()

@Actual
fun String.lowercaseWithCurrentLocaleNative(): String =
  toNSString().localizedLowercaseString()

@Actual
fun String.uppercaseWithCurrentLocaleNative(): String =
  toNSString().localizedUppercaseString()

@Actual
fun String.encodeUriComponentNative(): String = encodeUriComponentImpl(this)

private fun encodeUriComponentImpl(value: String): String {
  NSCharacterSet.URLQueryAllowedCharacterSet()
  val allowed = (NSCharacterSet.alphanumericCharacterSet.mutableCopy() as NSMutableCharacterSet).apply {
    addCharactersInString("-_.!~*'()")
  }

  return value.toNSString().stringByAddingPercentEncodingWithAllowedCharacters(allowed) ?: value
}

@Actual
fun String.decodeUriComponentNative(): String = decodeUriComponentImpl(this)

private fun decodeUriComponentImpl(value: String): String =
  value.toNSString().stringByRemovingPercentEncoding ?: value

@Actual
fun String.isValidUriStringNative(): Boolean = tryParseUrl(this)

private fun tryParseUrl(value: String): Boolean {
  return NSURL.URLWithString(value) != null
}

private fun String.toNSString(): NSString = this as NSString