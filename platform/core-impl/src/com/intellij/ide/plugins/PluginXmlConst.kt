// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginXmlConst {
  const val PLUGIN_PACKAGE_ATTR: String = "package"
  const val PLUGIN_IMPLEMENTATION_DETAIL_ATTR: String = "implementation-detail"
  const val PLUGIN_URL_ATTR: String = "url"
  const val PLUGIN_USE_IDEA_CLASSLOADER_ATTR: String = "use-idea-classloader"
  const val PLUGIN_REQUIRE_RESTART_ATTR: String = "require-restart"
  const val PLUGIN_ALLOW_BUNDLED_UPDATE_ATTR: String = "allow-bundled-update"
  const val PLUGIN_DEPENDENT_ON_CORE_ATTR: String = "dependent-on-core"
  const val PLUGIN_IS_SEPARATE_JAR_ATTR: String = "separate-jar"
  const val PLUGIN_VERSION_ATTR: String = "version"


  const val DEFAULT_XPOINTER_VALUE: String = "xpointer(/idea-plugin/*)"
}