// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.troubleshooting

import com.intellij.openapi.project.Project
import com.intellij.troubleshooting.GeneralTroubleInfoCollector
import org.jetbrains.annotations.ApiStatus
import java.lang.management.ManagementFactory

@ApiStatus.Internal
class GCTroubleInfoCollector : GeneralTroubleInfoCollector {
  override fun getTitle(): String = "Garbage Collection"

  override fun collectInfo(project: Project): String {
    return ManagementFactory.getGarbageCollectorMXBeans().joinToString("\n") {
      "Collector ${it.name}: count ${it.collectionCount}, total time ${it.collectionTime} ms"
    }
  }
}
