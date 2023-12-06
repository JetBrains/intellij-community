// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsActions
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import javax.swing.Icon

open class ToolbarAddQuickActionInfo(val actionIDs: List<String>,
                                     @NlsActions.ActionText val name: String,
                                     val icon: Icon,
                                     val insertStrategy: ToolbarQuickActionInsertStrategy)

class ToolbarAddQuickActionInfoBean: BaseKeyedLazyInstance<ToolbarAddQuickActionInfo>() {

  @Attribute("implementationClass")
  lateinit var implementationClass: String

  @Attribute("listGroupID")
  lateinit var listGroupID: String

  override fun getImplementationClassName(): String = implementationClass
}

val QUICK_ACTION_EP_NAME = ExtensionPointName.create<ToolbarAddQuickActionInfoBean>("com.intellij.toolbarQuickAction")