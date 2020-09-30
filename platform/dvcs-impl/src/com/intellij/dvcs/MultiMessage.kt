// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs

import com.intellij.dvcs.DvcsUtil.joinWithAnd
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsImplUtil.getShortVcsRootName
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class MultiRootMessage(project: Project, allValues: Collection<VirtualFile>, rootInPrefix: Boolean = false, html: Boolean = true) :
    MultiMessage<VirtualFile>(allValues, VirtualFile::getPath, { getShortVcsRootName(project, it) }, rootInPrefix, html)

open class MultiMessage<Aspect>(private val allValues: Collection<Aspect>,
                                private val logPresentation: (Aspect) -> @Nls String,
                                private val shortPresentation: (Aspect) -> @Nls String,
                                private val aspectInPrefix: Boolean,
                                html: Boolean = true) {
  private val LOG = Logger.getInstance(MultiMessage::class.java)
  private val messages = linkedMapOf<Aspect, String>()
  private val lineSeparator = if (html) "<br/>\n" else "\n" // NON-NLS

  fun append(aspect: Aspect, message: @Nls String): MultiMessage<Aspect> {
    if (!allValues.contains(aspect)) {
      LOG.error("The aspect value ${logPresentation(aspect)} is unexpected: $allValues")
      return this
    }
    if (messages.containsKey(aspect)) {
      LOG.error("Duplicate aspect value ${logPresentation(aspect)} reporting message [$message]")
    }
    messages.put(aspect, message)
    return this
  }

  fun asString(): @Nls String {
    if (messages.isEmpty()) return ""
    if (allValues.size == 1) return messages.values.first()
    val grouped = messages.keys.groupBy { messages[it]!!.trim() }
    if (grouped.size == 1 && allValues.size == messages.size) return messages.values.first()
    return grouped.keys.joinToString(lineSeparator) {
      val presentableNames = grouped.getValue(it).map { shortPresentation(it) }
      val names = joinWithAnd(presentableNames, 5)
      if (aspectInPrefix) {
        DvcsBundle.message("multi.message.line.prefix.form", names, it)
      }
      else {
        DvcsBundle.message("multi.message.line.suffix.form", names, it)
      }
    }
  }

  override fun toString(): @NonNls String {
    return messages.toString()
  }
}
