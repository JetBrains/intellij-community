// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {TRUNCATION_MARKER} from './shared'

export function isTruncatedText(text) {
  return findTruncationMarkerSuffix(text) >= 0 || findTruncationMarkerLine(text) >= 0
}

export function findTruncationMarkerSuffix(text) {
  if (text.endsWith(TRUNCATION_MARKER)) {
    return text.length - TRUNCATION_MARKER.length
  }
  if (text.endsWith(`${TRUNCATION_MARKER}\n`)) {
    return text.length - TRUNCATION_MARKER.length - 1
  }
  if (text.endsWith(`${TRUNCATION_MARKER}\r\n`)) {
    return text.length - TRUNCATION_MARKER.length - 2
  }
  return -1
}

export function findTruncationMarkerLine(text) {
  let index = text.indexOf(TRUNCATION_MARKER)
  while (index >= 0) {
    const beforeIndex = index - 1
    const afterIndex = index + TRUNCATION_MARKER.length
    const beforeOk = beforeIndex < 0 || isLineBreakChar(text.charCodeAt(beforeIndex))
    const afterOk = afterIndex >= text.length || isLineBreakChar(text.charCodeAt(afterIndex))
    if (beforeOk && afterOk) {
      return index
    }
    index = text.indexOf(TRUNCATION_MARKER, index + TRUNCATION_MARKER.length)
  }
  return -1
}

function isLineBreakChar(code) {
  return code === 10 || code === 13
}
