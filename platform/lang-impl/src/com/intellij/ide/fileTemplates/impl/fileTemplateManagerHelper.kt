// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl

import com.intellij.util.containers.MultiMap
import java.net.URL

internal data class FileTemplateLoadResult @JvmOverloads constructor(
  val result: MultiMap<String, DefaultTemplate>,
  var defaultTemplateDescription: URL? = null,
  var defaultIncludeDescription: URL? = null
)