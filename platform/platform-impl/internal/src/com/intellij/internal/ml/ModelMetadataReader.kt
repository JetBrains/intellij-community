// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

interface ModelMetadataReader {
  fun binaryFeatures(): String
  fun floatFeatures(): String
  fun categoricalFeatures(): String
  fun allKnown(): String
  fun featureOrderDirect(): List<String>
  fun extractVersion(): String?
}