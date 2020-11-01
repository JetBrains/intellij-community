// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl

import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute

/**
 * Provides bundled live templates.
 * See [Live Templates Tutorial](http://www.jetbrains.org/intellij/sdk/docs/tutorials/live_templates.html).
 *
 * @author yole
 */
internal class DefaultLiveTemplateEP {
  /**
   * Relative path to resource (e.g. `templates/customTemplates.xml`). `.xml` extension can be omitted.
   *
   * Note, that even though absolute path will work, it heavily depends on class-loader that loads resource,
   * so it's better to use relative path without leading slash since it will work for any class-loader.
   */
  @Attribute
  @RequiredElement
  @JvmField
  var file: String? = null

  /**
   * `true` if not user-visible/editable.
   */
  @Attribute
  @JvmField
  var hidden: Boolean = false
}
