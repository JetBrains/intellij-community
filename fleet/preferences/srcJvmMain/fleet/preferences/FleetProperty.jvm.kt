// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual

@Actual("fleetProperty")
fun fleetPropertyJvm(name: String, defaultValue: String?): String? {
  val formattedName = name.replace('.', '_').uppercase()
  return System.getProperty(name) ?: System.getenv(formattedName) ?: defaultValue
}