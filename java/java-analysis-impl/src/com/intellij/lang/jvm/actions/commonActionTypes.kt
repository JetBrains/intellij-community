// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message

object CreateMethodActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.method.from.usage.text", requireNotNull(data?.entityName))
  }
}

object CreateAbstractMethodActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.abstract.method.from.usage.text", requireNotNull(data?.entityName))
  }
}

object CreateFieldActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.field.from.usage.text", requireNotNull(data?.entityName))
  }
}

object CreateConstantActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.constant.from.usage.text", requireNotNull(data?.entityName))
  }
}

object CreateEnumConstantActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.enum.constant.from.usage.text", requireNotNull(data?.entityName))
  }
}

object CreatePropertyActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.property.from.usage.text", requireNotNull(data?.entityName))
  }
}

object CreateReadOnlyPropertyActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.read.only.property.from.usage.text", requireNotNull(data?.entityName))
  }
}

object CreateWriteOnlyPropertyActionGroup : JvmActionGroup {
  override fun getDisplayText(data: JvmActionGroup.RenderData?): String {
    return message("create.write.only.property.from.usage.text", requireNotNull(data?.entityName))
  }
}
