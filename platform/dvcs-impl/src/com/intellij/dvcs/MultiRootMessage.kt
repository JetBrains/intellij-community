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

class MultiRootMessage(private val project: Project, private val allRoots: Collection<VirtualFile>, html: Boolean) {
  private val LOG = Logger.getInstance(MultiRootMessage::class.java)
  private val messages = ContainerUtil.newLinkedHashMap<VirtualFile, String>()
  private val lineSeparator = if (html) "<br/>\n" else "\n"

  fun append(root: VirtualFile, message: String): MultiRootMessage {
    if (!allRoots.contains(root)) {
      LOG.error("The root ${root.path} is unexpected: $allRoots")
      return this
    }
    if (messages.containsKey(root)) {
      LOG.error("Duplicate root ${root.path} reporting message [$message]")
    }
    messages.put(root, message)
    return this
  }

  fun asString(): String {
    if (messages.isEmpty()) return "";
    if (allRoots.size == 1) return messages.values.first()
    if (messages.size == 1) {
      val (root, message) = messages.entries.first()
      return "$message in ${getShortVcsRootName(project, root)}"
    }
    val grouped = messages.keys.groupBy { messages[it]!!.trim() }
    if (grouped.size == 1 && allRoots.size == messages.size) return messages.values.first()
    return grouped.keys.joinToString(lineSeparator) {
      val shortRootNames = grouped[it]!!.map { getShortVcsRootName(project, it) }
      "$it in ${joinWithAnd(shortRootNames, 5)}" }
  }

  override fun toString(): String {
    return messages.toString()
  }
}
