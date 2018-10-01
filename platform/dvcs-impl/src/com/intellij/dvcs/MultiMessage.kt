/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dvcs

import com.intellij.dvcs.DvcsUtil.joinWithAnd
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcsUtil.VcsImplUtil.getShortVcsRootName

class MultiRootMessage(project: Project, allValues: Collection<VirtualFile>, rootInPrefix: Boolean = false, html: Boolean = true) :
    MultiMessage<VirtualFile>(allValues, VirtualFile::getPath, { getShortVcsRootName(project, it) }, rootInPrefix, html)

open class MultiMessage<Aspect>(private val allValues: Collection<Aspect>,
                                private val logPresentation: (Aspect) -> String,
                                private val shortPresentation: (Aspect) -> String,
                                private val aspectInPrefix: Boolean,
                                html: Boolean = true) {
  private val LOG = Logger.getInstance(MultiMessage::class.java)
  private val messages = ContainerUtil.newLinkedHashMap<Aspect, String>()
  private val lineSeparator = if (html) "<br/>\n" else "\n"

  fun append(aspect: Aspect, message: String): MultiMessage<Aspect> {
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

  fun asString(): String {
    if (messages.isEmpty()) return ""
    if (allValues.size == 1) return messages.values.first()
    if (messages.size == 1) {
      val (aspect, message) = messages.entries.first()
      return if (aspectInPrefix) "${shortPresentation(aspect)}: $message" else "$message in ${shortPresentation(aspect)}"
    }
    val grouped = messages.keys.groupBy { messages[it]!!.trim() }
    if (grouped.size == 1 && allValues.size == messages.size) return messages.values.first()
    return grouped.keys.joinToString(lineSeparator) {
      val presentableNames = grouped[it]!!.map { shortPresentation(it) }
      val names = joinWithAnd(presentableNames, 5)
      if (aspectInPrefix) "$names: $it" else "$it in $names"
    }
  }

  override fun toString(): String {
    return messages.toString()
  }
}
