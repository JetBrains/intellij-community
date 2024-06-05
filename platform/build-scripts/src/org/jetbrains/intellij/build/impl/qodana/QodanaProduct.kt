// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.qodana

internal enum class QodanaProduct(
  val productName: String,
  val productCode: String,
  val idePrefix: String,
) {
  QDJVM("Qodana for JVM", "QDJVM", "idea"),
  QDJVMC("Qodana Community for JVM", "QDJVMC", "Idea"),
  QDPY("Qodana for Python", "QDPY", "Python"),
  QDPYC("Qodana Community for Python", "QDPYC", "PyCharmCore"),
  QDJS("Qodana for JS", "QDJS", "WebStorm"),
  QDPHP("Qodana for PHP", "QDPHP", "PhpStorm"),
  QDRUBY("Qodana for Ruby", "QDRUBY", "Ruby"),
  QDGO("Qodana for Go", "QDGO", "GoLand"),
  QDRST("Qodana for Rust", "QDRST", "RustRover"),
  QDNET("Qodana for .NET", "QDNET", "Rider"),
}

internal val QODANA_PRODUCTS = QodanaProduct.entries.associateBy { it.idePrefix }