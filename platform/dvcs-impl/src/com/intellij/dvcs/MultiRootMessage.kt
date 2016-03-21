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

class MultiRootMessage(private val myProject: Project, private val myAllRoots: Collection<VirtualFile>, html: Boolean) {
  private val LOG = Logger.getInstance(MultiRootMessage::class.java)
  private val myMessages = ContainerUtil.newLinkedHashMap<VirtualFile, String>()
  private val myLineSeparator = if (html) "<br/>\n" else "\n"

  fun append(root: VirtualFile, message: String): MultiRootMessage {
    if (!myAllRoots.contains(root)) {
      LOG.error("The root ${root.path} is unexpected: $myAllRoots")
      return this
    }
    if (myMessages.containsKey(root)) {
      LOG.error("Duplicate root ${root.path} reporting message [$message]")
    }
    myMessages.put(root, message)
    return this
  }

  fun asString(): String {
    if (myMessages.isEmpty()) return "";
    if (myAllRoots.size == 1) return myMessages.values.first()
    if (myMessages.size == 1) {
      val (root, message) = myMessages.entries.first()
      return "$message in ${getShortVcsRootName(myProject, root)}"
    }
    val grouped = myMessages.keys.groupBy { myMessages[it]!!.trim() }
    if (grouped.size == 1 && myAllRoots.size == myMessages.size) return myMessages.values.first()
    return grouped.keys.joinToString(myLineSeparator) {
      val shortRootNames = grouped[it]!!.map { getShortVcsRootName(myProject, it) }
      "$it in ${joinWithAnd(shortRootNames, 5)}" }
  }

  override fun toString(): String {
    return myMessages.toString()
  }
}
