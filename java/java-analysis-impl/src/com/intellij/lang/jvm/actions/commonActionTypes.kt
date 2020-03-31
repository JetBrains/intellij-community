// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.psi.util.JavaElementKind

object CreateMethodActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.METHOD.`object`(), data?.entityName!!)
  }
}

object CreateAbstractMethodActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.ABSTRACT_METHOD.`object`(), data?.entityName!!)
  }
}

object CreateFieldActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.FIELD.`object`(), data?.entityName!!)
  }
}

object CreateConstantActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.CONSTANT.`object`(), data?.entityName!!)
  }
}

object CreateEnumConstantActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.ENUM_CONSTANT.`object`(), data?.entityName!!)
  }
}

object CreatePropertyActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.property.from.usage.text", data?.entityName!!)
  }
}

object CreateReadOnlyPropertyActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.read.only.property.from.usage.text", data?.entityName!!)
  }
}

object CreateWriteOnlyPropertyActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.write.only.property.from.usage.text", data?.entityName!!)
  }
}
