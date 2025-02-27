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

  const val ID_ELEM: String = "id"
  const val NAME_ELEM: String = "name"
  const val VERSION_ELEM: String = "version"
  const val IDEA_VERSION_ELEM: String = "idea-version"
  const val PRODUCT_DESCRIPTOR_ELEM: String = "product-descriptor"

  const val VENDOR_ELEM: String = "vendor"
  const val VENDOR_URL_ATTR: String = "url"
  const val VENDOR_EMAIL_ATTR: String = "email"

  const val CATEGORY_ELEM: String = "category"
  const val DESCRIPTION_ELEM: String = "description"
  const val CHANGE_NOTES_ELEM: String = "change-notes"

  const val RESOURCE_BUNDLE_ELEM: String = "resource-bundle"
  const val HELPSET_ELEM: String = "helpset"
  const val LOCALE_ELEM: String = "locale"

  const val MODULE_ELEM: String = "module"
  const val CONTENT_ELEM: String = "content"

  const val DEPENDS_ELEM: String = "depends"
  const val DEPENDENCIES_ELEM: String = "dependencies"
  const val INCOMPATIBLE_WITH_ELEM: String = "incompatible-with"

  const val ACTIONS_ELEM: String = "actions"

  const val APPLICATION_LISTENERS_ELEM: String = "applicationListeners"
  const val PROJECT_LISTENERS_ELEM: String = "projectListeners"

  const val EXTENSIONS_ELEM: String = "extensions"
  const val EXTENSION_POINTS_ELEM: String = "extensionPoints"

  const val APPLICATION_COMPONENTS_ELEM: String = "application-components"
  const val PROJECT_COMPONENTS_ELEM: String = "project-components"
  const val MODULE_COMPONENTS_ELEM: String = "module-components"

  const val INCLUDE_ELEM: String = "include"
  const val DEFAULT_XPOINTER_VALUE: String = "xpointer(/idea-plugin/*)"
}